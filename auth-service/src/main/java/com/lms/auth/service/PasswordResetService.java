package com.lms.auth.service;

import com.lms.auth.config.PasswordResetProperties;
import com.lms.auth.email.EmailService;
import com.lms.auth.entity.PasswordResetCode;
import com.lms.auth.entity.User;
import com.lms.auth.repository.PasswordResetCodeRepository;
import com.lms.auth.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetCodeRepository resetCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final PasswordResetProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetCodeRepository resetCodeRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            PasswordResetProperties properties) {
        this.userRepository = userRepository;
        this.resetCodeRepository = resetCodeRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.properties = properties;
    }

    @Transactional
    public void requestReset(String email) {
        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email);
        if (userOpt.isEmpty()) {
            // Anti-enumeration: return silently
            return;
        }
        User user = userOpt.get();

        Optional<PasswordResetCode> existingOpt =
                resetCodeRepository.findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(user.getId());

        if (existingOpt.isPresent()) {
            PasswordResetCode existing = existingOpt.get();
            Instant rateLimitThreshold = Instant.now().minus(1, ChronoUnit.MINUTES);
            if (existing.getCreatedAt().isAfter(rateLimitThreshold)) {
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Please wait before requesting another code.");
            }
        }

        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        String codeHash = passwordEncoder.encode(code);

        PasswordResetCode resetCode = new PasswordResetCode();
        resetCode.setUserId(user.getId());
        resetCode.setCodeHash(codeHash);
        resetCode.setExpiresAt(Instant.now().plus(properties.getCodeExpireMinutes(), ChronoUnit.MINUTES));
        resetCode.setAttempts(0);
        resetCode.setCreatedAt(Instant.now());

        resetCodeRepository.save(resetCode);

        emailService.sendResetCode(
                user.getEmail(),
                code,
                user.getFirstName() + " " + user.getLastName());
    }

    @Transactional
    public void resetPassword(String email, String code, String newPassword) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid code"));

        PasswordResetCode record = resetCodeRepository
                .findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Code expired or not found"));

        if (record.getAttempts() >= properties.getMaxAttempts()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts");
        }

        if (record.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Code has expired");
        }

        if (!passwordEncoder.matches(code, record.getCodeHash())) {
            record.setAttempts(record.getAttempts() + 1);
            resetCodeRepository.save(record);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid code");
        }

        record.setUsedAt(Instant.now());
        resetCodeRepository.save(record);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
    }
}

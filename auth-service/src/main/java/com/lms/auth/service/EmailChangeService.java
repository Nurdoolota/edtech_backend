package com.lms.auth.service;

import com.lms.auth.email.EmailService;
import com.lms.auth.entity.PendingEmailChange;
import com.lms.auth.entity.User;
import com.lms.auth.repository.PendingEmailChangeRepository;
import com.lms.auth.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EmailChangeService {

    private final UserRepository userRepository;
    private final PendingEmailChangeRepository pendingEmailChangeRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmailChangeService(
            UserRepository userRepository,
            PendingEmailChangeRepository pendingEmailChangeRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService) {
        this.userRepository = userRepository;
        this.pendingEmailChangeRepository = pendingEmailChangeRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @Transactional
    public void requestChange(Long userId, String newEmail, String currentPassword) {
        User user = userRepository.findByIdWithRole(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid password");
        }

        String normalizedEmail = newEmail.toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        String codeHash = passwordEncoder.encode(code);

        pendingEmailChangeRepository.deleteAllByUserId(userId);

        PendingEmailChange pending = new PendingEmailChange();
        pending.setUserId(userId);
        pending.setNewEmail(normalizedEmail);
        pending.setCodeHash(codeHash);
        pending.setExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));
        pending.setCreatedAt(Instant.now());
        pendingEmailChangeRepository.save(pending);

        emailService.sendEmailChangeCode(
                normalizedEmail,
                code,
                user.getFirstName() + " " + user.getLastName());
    }

    @Transactional
    public void confirmChange(Long userId, String code) {
        PendingEmailChange record = pendingEmailChangeRepository
                .findTopByUserIdOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "No pending email change found"));

        if (record.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Code has expired");
        }

        if (!passwordEncoder.matches(code, record.getCodeHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid code");
        }

        User user = userRepository.findByIdWithRole(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (userRepository.existsByEmailIgnoreCase(record.getNewEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already taken");
        }

        user.setEmail(record.getNewEmail());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        pendingEmailChangeRepository.delete(record);
    }
}

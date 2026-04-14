package com.lms.auth.service;

import com.lms.auth.dto.LoginRequest;
import com.lms.auth.dto.RefreshRequest;
import com.lms.auth.dto.RegisterRequest;
import com.lms.auth.dto.TokenResponse;
import com.lms.auth.dto.UserResponse;
import com.lms.auth.entity.RoleName;
import com.lms.auth.entity.User;
import com.lms.auth.exception.ApiBusinessException;
import com.lms.auth.repository.RoleRepository;
import com.lms.auth.repository.UserRepository;
import com.lms.auth.security.JwtService;
import com.lms.auth.security.JwtUserPrincipal;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ApiBusinessException("CONFLICT", HttpStatus.CONFLICT.value(), "Email already registered");
        }
        var role = roleRepository
                .findByName(RoleName.STUDENT)
                .orElseThrow(() -> new IllegalStateException("STUDENT role missing — run migrations"));
        User user = new User();
        user.setEmail(request.email().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setRole(role);
        user.setActive(true);
        userRepository.save(user);
        return UserMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user =
                userRepository
                        .findByEmailIgnoreCase(request.email().trim().toLowerCase())
                        .orElseThrow(
                                () -> new ApiBusinessException(
                                        "UNAUTHORIZED",
                                        HttpStatus.UNAUTHORIZED.value(),
                                        "Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiBusinessException(
                    "UNAUTHORIZED", HttpStatus.UNAUTHORIZED.value(), "Invalid email or password");
        }
        if (!user.isActive()) {
            throw new ApiBusinessException(
                    "FORBIDDEN",
                    HttpStatus.FORBIDDEN.value(),
                    "Account is disabled. Contact an administrator.");
        }
        var pair = jwtService.issueTokens(user, null);
        return TokenResponse.of(pair.accessToken(), pair.refreshToken(), pair.accessExpiresInSeconds());
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        try {
            Long userId = jwtService.parseRefreshUserId(request.refreshToken());
            User user =
                    userRepository
                            .findByIdWithRole(userId)
                            .orElseThrow(
                                    () -> new ApiBusinessException(
                                            "UNAUTHORIZED",
                                            HttpStatus.UNAUTHORIZED.value(),
                                            "Invalid refresh token"));
            if (!user.isActive()) {
                throw new ApiBusinessException(
                        "FORBIDDEN",
                        HttpStatus.FORBIDDEN.value(),
                        "Account is disabled. Contact an administrator.");
            }
            var pair = jwtService.issueTokens(user, null);
            return TokenResponse.of(pair.accessToken(), pair.refreshToken(), pair.accessExpiresInSeconds());
        } catch (JwtException e) {
            throw new ApiBusinessException(
                    "UNAUTHORIZED", HttpStatus.UNAUTHORIZED.value(), "Invalid refresh token");
        }
    }

    @Transactional(readOnly = true)
    public UserResponse me(Authentication authentication) {
        JwtUserPrincipal principal = (JwtUserPrincipal) authentication.getPrincipal();
        User user =
                userRepository
                        .findByIdWithRole(principal.getUserId())
                        .orElseThrow(() -> new ApiBusinessException("NOT_FOUND", HttpStatus.NOT_FOUND.value(), "User not found"));
        return UserMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse findStudentByEmail(String email) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        if (normalizedEmail.isBlank()) {
            throw new ApiBusinessException(
                    "VALIDATION_ERROR", HttpStatus.BAD_REQUEST.value(), "Email is required");
        }
        User user =
                userRepository
                        .findByEmailIgnoreCaseAndRole(normalizedEmail, RoleName.STUDENT)
                        .orElseThrow(
                                () ->
                                        new ApiBusinessException(
                                                "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Student not found"));
        return UserMapper.toResponse(user);
    }
}

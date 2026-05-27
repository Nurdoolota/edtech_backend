package com.lms.auth.controller;

import com.lms.auth.dto.AvatarUploadResponse;
import com.lms.auth.dto.ChangeEmailRequest;
import com.lms.auth.dto.ChangePasswordRequest;
import com.lms.auth.dto.ConfirmEmailRequest;
import com.lms.auth.dto.DeleteAccountRequest;
import com.lms.auth.dto.ForgotPasswordRequest;
import com.lms.auth.dto.LoginRequest;
import com.lms.auth.dto.ProfileUpdateRequest;
import com.lms.auth.dto.RefreshRequest;
import com.lms.auth.dto.RegisterRequest;
import com.lms.auth.dto.ResetPasswordRequest;
import com.lms.auth.dto.TokenResponse;
import com.lms.auth.dto.UserResponse;
import com.lms.auth.entity.RoleName;
import com.lms.auth.entity.User;
import com.lms.auth.exception.ApiBusinessException;
import com.lms.auth.repository.UserRepository;
import com.lms.auth.security.JwtUserPrincipal;
import com.lms.auth.service.AuthService;
import com.lms.auth.service.AvatarService;
import com.lms.auth.service.EmailChangeService;
import com.lms.auth.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
public class AuthController {

    private final AuthService authService;
    private final AvatarService avatarService;
    private final UserRepository userRepository;
    private final PasswordResetService passwordResetService;
    private final EmailChangeService emailChangeService;

    public AuthController(AuthService authService,
                          AvatarService avatarService,
                          UserRepository userRepository,
                          PasswordResetService passwordResetService,
                          EmailChangeService emailChangeService) {
        this.authService = authService;
        this.avatarService = avatarService;
        this.userRepository = userRepository;
        this.passwordResetService = passwordResetService;
        this.emailChangeService = emailChangeService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register (role fixed to STUDENT)")
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Login; returns access and refresh JWT")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token; returns new access and refresh pair")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Current user from access JWT")
    public UserResponse me(Authentication authentication) {
        return authService.me(authentication);
    }

    @PatchMapping("/me")
    @Operation(summary = "Partial profile update (gateway injects X-User-Id)")
    public UserResponse updateProfile(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody ProfileUpdateRequest request) {
        return authService.updateProfile(userId, request);
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change password (gateway injects X-User-Id)")
    public ResponseEntity<Void> changePassword(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(userId, request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete account with password confirmation")
    public void deleteAccount(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody DeleteAccountRequest request) {
        authService.deleteAccount(userId, request);
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload avatar image (PNG/JPEG, max 2 MB)")
    public AvatarUploadResponse uploadAvatar(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam("file") MultipartFile file) {
        String url = avatarService.save(userId, file);
        User user = userRepository.findByIdWithRole(userId)
                .orElseThrow(() -> new ApiBusinessException(
                        "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "User not found"));
        user.setAvatarUrl(url);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        return new AvatarUploadResponse(url);
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a 6-digit password reset code via email")
    public Map<String, Boolean> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.email());
        return Map.of("sent", true);
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Verify 6-digit code and set a new password")
    public Map<String, Boolean> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.email(), request.code(), request.newPassword());
        return Map.of("reset", true);
    }

    @PostMapping("/change-email")
    @Operation(summary = "Initiate email change — sends 6-digit code to new address")
    public Map<String, Boolean> changeEmail(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody ChangeEmailRequest request) {
        emailChangeService.requestChange(userId, request.newEmail(), request.password());
        return Map.of("sent", true);
    }

    @PostMapping("/confirm-email")
    @Operation(summary = "Confirm email change with 6-digit code")
    public Map<String, Boolean> confirmEmail(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody ConfirmEmailRequest request) {
        emailChangeService.confirmChange(userId, request.code());
        return Map.of("emailChanged", true);
    }

    @GetMapping("/students/by-email")
    @Operation(summary = "Find student by email (TEACHER, ADMIN)")
    public UserResponse findStudentByEmail(
            Authentication authentication, @RequestParam String email) {
        ensureTeacherOrAdmin(authentication);
        return authService.findStudentByEmail(email);
    }

    private static void ensureTeacherOrAdmin(Authentication authentication) {
        JwtUserPrincipal principal = (JwtUserPrincipal) authentication.getPrincipal();
        if (principal.getRole() != RoleName.ADMIN && principal.getRole() != RoleName.TEACHER) {
            throw new ApiBusinessException(
                    "FORBIDDEN", HttpStatus.FORBIDDEN.value(), "Access denied");
        }
    }
}

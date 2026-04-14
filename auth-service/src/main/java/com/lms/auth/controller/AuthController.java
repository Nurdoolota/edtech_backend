package com.lms.auth.controller;

import com.lms.auth.dto.LoginRequest;
import com.lms.auth.dto.RefreshRequest;
import com.lms.auth.dto.RegisterRequest;
import com.lms.auth.dto.TokenResponse;
import com.lms.auth.dto.UserResponse;
import com.lms.auth.entity.RoleName;
import com.lms.auth.exception.ApiBusinessException;
import com.lms.auth.security.JwtUserPrincipal;
import com.lms.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
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

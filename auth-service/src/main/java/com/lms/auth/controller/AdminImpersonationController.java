package com.lms.auth.controller;

import com.lms.auth.dto.ImpersonateRequest;
import com.lms.auth.dto.TokenResponse;
import com.lms.auth.exception.ApiBusinessException;
import com.lms.auth.security.JwtUserPrincipal;
import com.lms.auth.service.ImpersonationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin impersonation")
public class AdminImpersonationController {

    private final ImpersonationService impersonationService;

    public AdminImpersonationController(ImpersonationService impersonationService) {
        this.impersonationService = impersonationService;
    }

    @PostMapping("/impersonate")
    @Operation(summary = "Issue access token for target user (audit row created)")
    public TokenResponse impersonate(
            Authentication authentication, @Valid @RequestBody ImpersonateRequest request) {
        rejectIfImpersonating(authentication);
        return impersonationService.start(authentication, request);
    }

    @PostMapping("/impersonate/stop")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "End impersonation (call with impersonation access token)")
    public void stop(Authentication authentication) {
        impersonationService.stop(authentication);
    }

    private static void rejectIfImpersonating(Authentication authentication) {
        JwtUserPrincipal p = (JwtUserPrincipal) authentication.getPrincipal();
        if (p.isImpersonating()) {
            throw new ApiBusinessException(
                    "FORBIDDEN", HttpStatus.FORBIDDEN.value(), "Already impersonating");
        }
    }
}

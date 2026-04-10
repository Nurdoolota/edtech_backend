package com.lms.auth.service;

import com.lms.auth.dto.ImpersonateRequest;
import com.lms.auth.dto.TokenResponse;
import com.lms.auth.entity.ImpersonationAudit;
import com.lms.auth.entity.RoleName;
import com.lms.auth.entity.User;
import com.lms.auth.exception.ApiBusinessException;
import com.lms.auth.repository.ImpersonationAuditRepository;
import com.lms.auth.repository.UserRepository;
import com.lms.auth.security.JwtService;
import com.lms.auth.security.JwtUserPrincipal;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class ImpersonationService {

    private final UserRepository userRepository;
    private final ImpersonationAuditRepository impersonationAuditRepository;
    private final JwtService jwtService;

    public ImpersonationService(
            UserRepository userRepository,
            ImpersonationAuditRepository impersonationAuditRepository,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.impersonationAuditRepository = impersonationAuditRepository;
        this.jwtService = jwtService;
    }

    @Transactional
    public TokenResponse start(Authentication authentication, ImpersonateRequest request) {
        JwtUserPrincipal adminPrincipal = (JwtUserPrincipal) authentication.getPrincipal();
        if (adminPrincipal.isImpersonating()) {
            throw new ApiBusinessException(
                    "FORBIDDEN",
                    HttpStatus.FORBIDDEN.value(),
                    "Cannot start impersonation while already impersonating");
        }
        User admin =
                userRepository
                        .findByIdWithRole(adminPrincipal.getUserId())
                        .orElseThrow(
                                () -> new ApiBusinessException(
                                        "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "User not found"));
        User target =
                userRepository
                        .findByIdWithRole(request.targetUserId())
                        .orElseThrow(
                                () -> new ApiBusinessException(
                                        "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "Target user not found"));
        if (target.getRole().getName() == RoleName.ADMIN) {
            throw new ApiBusinessException(
                    "FORBIDDEN", HttpStatus.FORBIDDEN.value(), "Cannot impersonate an administrator");
        }
        if (!target.isActive()) {
            throw new ApiBusinessException(
                    "FORBIDDEN", HttpStatus.FORBIDDEN.value(), "Target account is disabled");
        }

        var audit = new ImpersonationAudit();
        audit.setAdmin(admin);
        audit.setTargetUser(target);
        audit.setStartedAt(Instant.now());
        audit.setIpAddress(currentIp());
        impersonationAuditRepository.save(audit);

        var pair = jwtService.issueTokens(target, admin.getId());
        return TokenResponse.accessOnly(pair.accessToken(), pair.accessExpiresInSeconds());
    }

    @Transactional
    public void stop(Authentication authentication) {
        JwtUserPrincipal principal = (JwtUserPrincipal) authentication.getPrincipal();
        if (!principal.isImpersonating()) {
            throw new ApiBusinessException(
                    "BAD_REQUEST",
                    HttpStatus.BAD_REQUEST.value(),
                    "Not an impersonation session");
        }
        var audit =
                impersonationAuditRepository
                        .findByAdmin_IdAndTargetUser_IdAndEndedAtIsNull(
                                principal.getImpersonatorId(), principal.getUserId())
                        .orElseThrow(
                                () -> new ApiBusinessException(
                                        "NOT_FOUND",
                                        HttpStatus.NOT_FOUND.value(),
                                        "No active impersonation session found"));
        audit.setEndedAt(Instant.now());
    }

    private static String currentIp() {
        var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        return attrs.getRequest().getRemoteAddr();
    }
}

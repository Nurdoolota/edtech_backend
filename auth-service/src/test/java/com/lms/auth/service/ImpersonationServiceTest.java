package com.lms.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lms.auth.dto.ImpersonateRequest;
import com.lms.auth.entity.ImpersonationAudit;
import com.lms.auth.entity.Role;
import com.lms.auth.entity.RoleName;
import com.lms.auth.entity.User;
import com.lms.auth.exception.ApiBusinessException;
import com.lms.auth.repository.ImpersonationAuditRepository;
import com.lms.auth.repository.UserRepository;
import com.lms.auth.security.JwtService;
import com.lms.auth.security.JwtUserPrincipal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class ImpersonationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private ImpersonationAuditRepository auditRepository;
    @Mock private JwtService jwtService;

    @InjectMocks private ImpersonationService impersonationService;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Role role(RoleName name) {
        Role r = new Role();
        r.setName(name);
        return r;
    }

    private static User user(long id, RoleName roleName, boolean active) {
        User u = new User();
        u.setEmail("user" + id + "@test.com");
        u.setPasswordHash("{bcrypt}x");
        u.setFirstName("F");
        u.setLastName("L");
        u.setRole(role(roleName));
        u.setActive(active);
        try {
            var f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(u, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return u;
    }

    private static Authentication adminAuth(long adminId, boolean isImpersonating) {
        Long imp = isImpersonating ? 99L : null;
        JwtUserPrincipal p = new JwtUserPrincipal(adminId, "admin@test.com", RoleName.ADMIN, imp);
        return new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
    }

    private static Authentication userAuth(long userId, long impersonatorId) {
        JwtUserPrincipal p = new JwtUserPrincipal(userId, "u@test.com", RoleName.STUDENT, impersonatorId);
        return new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
    }

    // ── start ─────────────────────────────────────────────────────────────────

    /** Cannot start impersonation when already in an impersonation session. */
    @Test
    void start_rejectsIfAlreadyImpersonating() {
        Authentication auth = adminAuth(1L, true);
        ApiBusinessException ex = assertThrows(ApiBusinessException.class,
                () -> impersonationService.start(auth, new ImpersonateRequest(2L)));
        assertEquals("FORBIDDEN", ex.getCode());
    }

    /** Cannot impersonate another admin. */
    @Test
    void start_rejectsTargetAdmin() {
        User admin = user(1L, RoleName.ADMIN, true);
        User target = user(2L, RoleName.ADMIN, true);
        when(userRepository.findByIdWithRole(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findByIdWithRole(2L)).thenReturn(Optional.of(target));

        Authentication auth = adminAuth(1L, false);
        ApiBusinessException ex = assertThrows(ApiBusinessException.class,
                () -> impersonationService.start(auth, new ImpersonateRequest(2L)));
        assertEquals("FORBIDDEN", ex.getCode());
    }

    /** Cannot impersonate a disabled user. */
    @Test
    void start_rejectsInactiveTarget() {
        User admin = user(1L, RoleName.ADMIN, true);
        User target = user(2L, RoleName.STUDENT, false);
        when(userRepository.findByIdWithRole(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findByIdWithRole(2L)).thenReturn(Optional.of(target));

        Authentication auth = adminAuth(1L, false);
        ApiBusinessException ex = assertThrows(ApiBusinessException.class,
                () -> impersonationService.start(auth, new ImpersonateRequest(2L)));
        assertEquals("FORBIDDEN", ex.getCode());
    }

    /** Successful start saves audit row with admin, target, startedAt set. */
    @Test
    void start_success_savesAudit() {
        User admin = user(1L, RoleName.ADMIN, true);
        User target = user(2L, RoleName.STUDENT, true);
        when(userRepository.findByIdWithRole(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findByIdWithRole(2L)).thenReturn(Optional.of(target));
        when(auditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.issueTokens(target, 1L))
                .thenReturn(new JwtService.TokenPair("imp-access", null, 900L));

        Authentication auth = adminAuth(1L, false);
        var resp = impersonationService.start(auth, new ImpersonateRequest(2L));
        assertEquals("imp-access", resp.accessToken());

        ArgumentCaptor<ImpersonationAudit> captor = ArgumentCaptor.forClass(ImpersonationAudit.class);
        verify(auditRepository).save(captor.capture());
        assertNotNull(captor.getValue().getStartedAt());
        assertEquals(admin, captor.getValue().getAdmin());
        assertEquals(target, captor.getValue().getTargetUser());
    }

    // ── stop ──────────────────────────────────────────────────────────────────

    /** Calling stop with a non-impersonation token is rejected. */
    @Test
    void stop_rejectsNonImpersonationToken() {
        JwtUserPrincipal p = new JwtUserPrincipal(1L, "u@t.com", RoleName.STUDENT, null);
        Authentication auth = new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
        ApiBusinessException ex = assertThrows(ApiBusinessException.class,
                () -> impersonationService.stop(auth));
        assertEquals("BAD_REQUEST", ex.getCode());
    }

    /** Successful stop sets endedAt on the audit row. */
    @Test
    void stop_success_setsEndedAt() {
        ImpersonationAudit audit = new ImpersonationAudit();
        when(auditRepository.findByAdmin_IdAndTargetUser_IdAndEndedAtIsNull(10L, 2L))
                .thenReturn(Optional.of(audit));

        Authentication auth = userAuth(2L, 10L);
        impersonationService.stop(auth);

        assertNotNull(audit.getEndedAt());
    }

    /** stop() when no active audit row → NOT_FOUND. */
    @Test
    void stop_noActiveAudit_notFound() {
        when(auditRepository.findByAdmin_IdAndTargetUser_IdAndEndedAtIsNull(10L, 2L))
                .thenReturn(Optional.empty());
        Authentication auth = userAuth(2L, 10L);
        ApiBusinessException ex = assertThrows(ApiBusinessException.class,
                () -> impersonationService.stop(auth));
        assertEquals("NOT_FOUND", ex.getCode());
    }
}

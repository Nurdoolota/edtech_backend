package com.lms.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lms.auth.dto.ChangePasswordRequest;
import com.lms.auth.dto.DeleteAccountRequest;
import com.lms.auth.dto.LoginRequest;
import com.lms.auth.dto.ProfileUpdateRequest;
import com.lms.auth.dto.RefreshRequest;
import com.lms.auth.dto.RegisterRequest;
import com.lms.auth.dto.TokenResponse;
import com.lms.auth.dto.UserResponse;
import com.lms.auth.entity.Role;
import com.lms.auth.entity.RoleName;
import com.lms.auth.entity.User;
import com.lms.auth.exception.ApiBusinessException;
import com.lms.auth.repository.RoleRepository;
import com.lms.auth.repository.UserRepository;
import com.lms.auth.security.JwtService;
import com.lms.auth.security.JwtUserPrincipal;
import io.jsonwebtoken.JwtException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Unit tests for AuthService covering FR-001..FR-005. */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    @InjectMocks private AuthService authService;

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static Role role(RoleName name) {
        Role r = new Role();
        r.setName(name);
        return r;
    }

    private static User user(long id, String email, Role role, boolean active) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash("{bcrypt}hashed");
        u.setFirstName("First");
        u.setLastName("Last");
        u.setRole(role);
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

    private static JwtService.TokenPair tokenPair() {
        return new JwtService.TokenPair("access-tok", "refresh-tok", 900L);
    }

    // ── FR-001 Registration ───────────────────────────────────────────────────

    /** FR-001: duplicate email rejected. */
    @Test
    void register_rejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("a@b.c")).thenReturn(true);
        var req = new RegisterRequest("a@b.c", "password12", "A", "B");
        ApiBusinessException ex = assertThrows(ApiBusinessException.class, () -> authService.register(req));
        assertEquals("CONFLICT", ex.getCode());
    }

    /** FR-001: successful registration assigns STUDENT role and returns response. */
    @Test
    void register_success_assignsStudentRole() {
        Role student = role(RoleName.STUDENT);
        when(userRepository.existsByEmailIgnoreCase("New@B.C")).thenReturn(false);
        when(roleRepository.findByName(RoleName.STUDENT)).thenReturn(Optional.of(student));
        when(passwordEncoder.encode("password12")).thenReturn("{bcrypt}hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            try {
                var f = User.class.getDeclaredField("id");
                f.setAccessible(true);
                f.set(u, 1L);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return u;
        });

        UserResponse resp = authService.register(new RegisterRequest("New@B.C", "password12", "Alice", "Smith"));

        assertEquals("new@b.c", resp.email());
        assertEquals(RoleName.STUDENT, resp.role());
        verify(userRepository).save(any(User.class));
    }

    /** FR-001: missing STUDENT role in DB throws IllegalStateException. */
    @Test
    void register_missingStudentRole_throwsIllegalState() {
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(roleRepository.findByName(RoleName.STUDENT)).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class,
                () -> authService.register(new RegisterRequest("x@y.z", "password12", "A", "B")));
    }

    // ── FR-002 Login ──────────────────────────────────────────────────────────

    /** FR-002: successful login returns access + refresh tokens. */
    @Test
    void login_success_returnsBothTokens() {
        Role student = role(RoleName.STUDENT);
        User u = user(1L, "a@b.c", student, true);
        when(userRepository.findByEmailIgnoreCase("a@b.c")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("pass1234", u.getPasswordHash())).thenReturn(true);
        when(jwtService.issueTokens(u, null)).thenReturn(tokenPair());

        TokenResponse resp = authService.login(new LoginRequest("A@B.C", "pass1234"));

        assertEquals("access-tok", resp.accessToken());
        assertEquals("refresh-tok", resp.refreshToken());
    }

    /** FR-002: unknown email → UNAUTHORIZED. */
    @Test
    void login_unknownEmail_unauthorized() {
        when(userRepository.findByEmailIgnoreCase("x@y.z")).thenReturn(Optional.empty());
        ApiBusinessException ex = assertThrows(ApiBusinessException.class,
                () -> authService.login(new LoginRequest("x@y.z", "pass")));
        assertEquals("UNAUTHORIZED", ex.getCode());
    }

    /** FR-002: wrong password → UNAUTHORIZED. */
    @Test
    void login_wrongPassword_unauthorized() {
        Role student = role(RoleName.STUDENT);
        User u = user(1L, "a@b.c", student, true);
        when(userRepository.findByEmailIgnoreCase("a@b.c")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("wrong", u.getPasswordHash())).thenReturn(false);
        ApiBusinessException ex = assertThrows(ApiBusinessException.class,
                () -> authService.login(new LoginRequest("a@b.c", "wrong")));
        assertEquals("UNAUTHORIZED", ex.getCode());
    }

    // ── FR-005 Inactive account ───────────────────────────────────────────────

    /** FR-005: inactive account → FORBIDDEN with clear message. */
    @Test
    void login_inactiveAccount_forbidden() {
        Role student = role(RoleName.STUDENT);
        User u = user(1L, "a@b.c", student, false);
        when(userRepository.findByEmailIgnoreCase("a@b.c")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("pass1234", u.getPasswordHash())).thenReturn(true);
        ApiBusinessException ex = assertThrows(ApiBusinessException.class,
                () -> authService.login(new LoginRequest("a@b.c", "pass1234")));
        assertEquals("FORBIDDEN", ex.getCode());
        assertNotNull(ex.getMessage());
    }

    // ── FR-003 Refresh token rotation ─────────────────────────────────────────

    /** FR-003: valid refresh token issues new token pair. */
    @Test
    void refresh_success_returnsNewTokens() {
        Role student = role(RoleName.STUDENT);
        User u = user(1L, "a@b.c", student, true);
        when(jwtService.parseRefreshUserId("old-refresh")).thenReturn(1L);
        when(userRepository.findByIdWithRole(1L)).thenReturn(Optional.of(u));
        when(jwtService.issueTokens(u, null)).thenReturn(tokenPair());

        TokenResponse resp = authService.refresh(new RefreshRequest("old-refresh"));
        assertEquals("access-tok", resp.accessToken());
    }

    /** FR-003: invalid JWT → UNAUTHORIZED. */
    @Test
    void refresh_invalidToken_unauthorized() {
        when(jwtService.parseRefreshUserId("bad")).thenThrow(new JwtException("bad token"));
        ApiBusinessException ex = assertThrows(ApiBusinessException.class,
                () -> authService.refresh(new RefreshRequest("bad")));
        assertEquals("UNAUTHORIZED", ex.getCode());
    }

    /** FR-003 / FR-005: refresh with inactive account → FORBIDDEN. */
    @Test
    void refresh_inactiveAccount_forbidden() {
        Role student = role(RoleName.STUDENT);
        User u = user(1L, "a@b.c", student, false);
        when(jwtService.parseRefreshUserId("tok")).thenReturn(1L);
        when(userRepository.findByIdWithRole(1L)).thenReturn(Optional.of(u));
        ApiBusinessException ex = assertThrows(ApiBusinessException.class,
                () -> authService.refresh(new RefreshRequest("tok")));
        assertEquals("FORBIDDEN", ex.getCode());
    }

    // ── FR-004 /me ────────────────────────────────────────────────────────────

    /** FR-004: me() returns user data from JWT principal. */
    @Test
    void me_returnsCurrentUser() {
        Role student = role(RoleName.STUDENT);
        User u = user(42L, "me@example.com", student, true);
        JwtUserPrincipal principal = new JwtUserPrincipal(42L, "me@example.com", RoleName.STUDENT, null);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(userRepository.findByIdWithRole(42L)).thenReturn(Optional.of(u));

        UserResponse resp = authService.me(auth);
        assertEquals(42L, resp.id());
        assertEquals("me@example.com", resp.email());
    }

    /** FR-004: me() for non-existent user → NOT_FOUND. */
    @Test
    void me_userNotFound() {
        JwtUserPrincipal principal = new JwtUserPrincipal(99L, "ghost@x.com", RoleName.STUDENT, null);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(userRepository.findByIdWithRole(99L)).thenReturn(Optional.empty());
        ApiBusinessException ex = assertThrows(ApiBusinessException.class, () -> authService.me(auth));
        assertEquals("NOT_FOUND", ex.getCode());
    }

    // ── Profile update (PATCH /me) ────────────────────────────────────────────

    /** updateProfile applies only non-null fields and returns updated response. */
    @Test
    void updateProfile_success_appliesNonNullFields() {
        Role student = role(RoleName.STUDENT);
        User u = user(42L, "me@example.com", student, true);
        when(userRepository.findByIdWithRole(42L)).thenReturn(Optional.of(u));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new ProfileUpdateRequest("Ivan", null, "Bio text", "en", null, true, null);
        UserResponse resp = authService.updateProfile(42L, req);

        assertEquals("Ivan", resp.firstName());
        assertEquals("Last", resp.lastName()); // unchanged
        assertEquals("Bio text", resp.bio());
        assertEquals("en", resp.locale());
        assertEquals("UTC", resp.timezone()); // unchanged
        assertEquals(true, resp.emailPrivate());
    }

    /** updateProfile with non-existent user → NOT_FOUND. */
    @Test
    void updateProfile_userNotFound() {
        when(userRepository.findByIdWithRole(99L)).thenReturn(Optional.empty());
        var req = new ProfileUpdateRequest(null, null, null, null, null, null, null);
        ApiBusinessException ex = assertThrows(ApiBusinessException.class,
                () -> authService.updateProfile(99L, req));
        assertEquals("NOT_FOUND", ex.getCode());
    }

    // ── Delete account (DELETE /me) ───────────────────────────────────────────

    /** deleteAccount with correct password soft-deletes the user. */
    @Test
    void deleteAccount_success_setsInactive() {
        Role student = role(RoleName.STUDENT);
        User u = user(7L, "del@example.com", student, true);
        when(userRepository.findByIdWithRole(7L)).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("correct", u.getPasswordHash())).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.deleteAccount(7L, new DeleteAccountRequest("correct"));

        assertEquals(false, u.isActive());
        assertEquals("deleted-7@deleted.invalid", u.getEmail());
    }

    /** deleteAccount with wrong password → UNAUTHORIZED, user unchanged. */
    @Test
    void deleteAccount_wrongPassword_unauthorized() {
        Role student = role(RoleName.STUDENT);
        User u = user(7L, "del@example.com", student, true);
        when(userRepository.findByIdWithRole(7L)).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("wrong", u.getPasswordHash())).thenReturn(false);

        ApiBusinessException ex = assertThrows(ApiBusinessException.class,
                () -> authService.deleteAccount(7L, new DeleteAccountRequest("wrong")));
        assertEquals("UNAUTHORIZED", ex.getCode());
        assertEquals(true, u.isActive()); // unchanged
    }

    // ── Change password (POST /change-password) ───────────────────────────────

    /** changePassword with correct current password updates the hash. */
    @Test
    void changePassword_success_updatesHash() {
        Role student = role(RoleName.STUDENT);
        User u = user(5L, "user@example.com", student, true);
        when(userRepository.findByIdWithRole(5L)).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("oldPass1", u.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.encode("newPass99")).thenReturn("{bcrypt}newhash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.changePassword(5L, "oldPass1", "newPass99");

        assertEquals("{bcrypt}newhash", u.getPasswordHash());
        verify(userRepository).save(u);
    }

    /** changePassword with wrong current password → UNAUTHORIZED. */
    @Test
    void changePassword_wrongCurrentPassword_unauthorized() {
        Role student = role(RoleName.STUDENT);
        User u = user(5L, "user@example.com", student, true);
        when(userRepository.findByIdWithRole(5L)).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("wrongOld", u.getPasswordHash())).thenReturn(false);

        ApiBusinessException ex = assertThrows(ApiBusinessException.class,
                () -> authService.changePassword(5L, "wrongOld", "newPass99"));
        assertEquals("UNAUTHORIZED", ex.getCode());
    }

    /** changePassword with unknown userId → NOT_FOUND. */
    @Test
    void changePassword_userNotFound() {
        when(userRepository.findByIdWithRole(999L)).thenReturn(Optional.empty());

        ApiBusinessException ex = assertThrows(ApiBusinessException.class,
                () -> authService.changePassword(999L, "any", "newPass99"));
        assertEquals("NOT_FOUND", ex.getCode());
    }
}

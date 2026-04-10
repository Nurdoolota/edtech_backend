package com.lms.auth.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lms.auth.config.JwtProperties;
import com.lms.auth.entity.Role;
import com.lms.auth.entity.RoleName;
import com.lms.auth.entity.User;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String LONG_ASCII_SECRET =
            "this-is-a-long-ascii-secret-that-is-at-least-32-bytes";
    private static final String SHORT_ASCII_SECRET = "short";
    private static final String BASE64_SECRET =
            "aGVsbG8td29ybGQtdGhpcy1pcy1hLWxvbmctZW5vdWdoLXNlY3JldA=="; // ≥32 decoded bytes

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = buildService(LONG_ASCII_SECRET);
    }

    // ── resolveKey resilience ─────────────────────────────────────────────────

    /** Long ASCII secret (no base64 chars issue) — must not throw. */
    @Test
    void resolveKey_longAsciiSecret_doesNotThrow() {
        assertNotNull(buildService(LONG_ASCII_SECRET));
    }

    /** Short ASCII secret (< 32 bytes) — SHA-256 stretch, must not throw WeakKeyException. */
    @Test
    void resolveKey_shortAsciiSecret_doesNotThrow() {
        assertNotNull(buildService(SHORT_ASCII_SECRET));
    }

    /** Base64-encoded secret with enough decoded bytes — must not throw. */
    @Test
    void resolveKey_validBase64Secret_doesNotThrow() {
        assertNotNull(buildService(BASE64_SECRET));
    }

    /** Secret with URL-safe base64 chars ('_') — must fall back to UTF-8, not throw. */
    @Test
    void resolveKey_urlSafeBase64Chars_doesNotThrow() {
        assertNotNull(buildService("secret_with_underscore_and-dash-chars-long-enough"));
    }

    // ── Token issuance / parsing round-trip ──────────────────────────────────

    @Test
    void createAccessToken_parseBack_hasExpectedClaims() {
        User u = userWithRole(7L, RoleName.STUDENT);
        String token = jwtService.createAccessToken(u, null);
        JwtUserPrincipal principal = jwtService.parseAccessPrincipal(token);

        assertEquals(7L, principal.getUserId());
        assertEquals(RoleName.STUDENT, principal.getRole());
        assertNull(principal.getImpersonatorId());
    }

    @Test
    void createAccessToken_withImpersonator_impersonatorIdPreserved() {
        User u = userWithRole(2L, RoleName.STUDENT);
        String token = jwtService.createAccessToken(u, 99L);
        JwtUserPrincipal principal = jwtService.parseAccessPrincipal(token);

        assertEquals(2L, principal.getUserId());
        assertEquals(99L, principal.getImpersonatorId());
    }

    @Test
    void parseAccessPrincipal_rejectsRefreshToken() {
        User u = userWithRole(1L, RoleName.STUDENT);
        String refreshToken = jwtService.createRefreshToken(u);
        assertThrows(JwtException.class, () -> jwtService.parseAccessPrincipal(refreshToken));
    }

    @Test
    void parseRefreshUserId_rejectsAccessToken() {
        User u = userWithRole(1L, RoleName.STUDENT);
        String accessToken = jwtService.createAccessToken(u, null);
        assertThrows(JwtException.class, () -> jwtService.parseRefreshUserId(accessToken));
    }

    @Test
    void issueTokens_withoutImpersonation_returnsBothTokens() {
        User u = userWithRole(5L, RoleName.TEACHER);
        var pair = jwtService.issueTokens(u, null);
        assertNotNull(pair.accessToken());
        assertNotNull(pair.refreshToken());
    }

    @Test
    void issueTokens_withImpersonation_refreshTokenIsNull() {
        User u = userWithRole(5L, RoleName.STUDENT);
        var pair = jwtService.issueTokens(u, 1L);
        assertNotNull(pair.accessToken());
        assertNull(pair.refreshToken());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static JwtService buildService(String secret) {
        JwtProperties props = new JwtProperties();
        props.setSecret(secret);
        props.setAccessExpirySeconds(900);
        props.setRefreshExpirySeconds(604800);
        return new JwtService(props);
    }

    private static User userWithRole(long id, RoleName roleName) {
        Role role = new Role();
        role.setName(roleName);
        User u = new User();
        u.setEmail("u" + id + "@test.com");
        u.setPasswordHash("{bcrypt}x");
        u.setFirstName("T");
        u.setLastName("U");
        u.setRole(role);
        u.setActive(true);
        try {
            var f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(u, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return u;
    }
}

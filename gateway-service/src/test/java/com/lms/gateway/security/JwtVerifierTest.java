package com.lms.gateway.security;

import com.lms.gateway.config.JwtProperties;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtVerifierTest {

    private static final String SECRET = "test-secret-that-is-long-enough-for-hmac-sha256-at-least-32-bytes";

    private JwtVerifier verifier;
    private SecretKey secretKey;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret(SECRET);
        verifier = new JwtVerifier(props);
        secretKey = JwtVerifier.resolveKey(SECRET);
    }

    @Test
    void parseAccess_validToken_returnsClaims() {
        String token = buildAccessToken(42L, "STUDENT", null, Instant.now().plusSeconds(900));

        JwtVerifier.JwtClaims claims = verifier.parseAccess(token);

        assertThat(claims.userId()).isEqualTo(42L);
        assertThat(claims.role()).isEqualTo("STUDENT");
        assertThat(claims.impersonatorId()).isNull();
    }

    @Test
    void parseAccess_tokenWithImpersonator_returnsImpersonatorId() {
        String token = buildAccessToken(5L, "STUDENT", 1L, Instant.now().plusSeconds(900));

        JwtVerifier.JwtClaims claims = verifier.parseAccess(token);

        assertThat(claims.userId()).isEqualTo(5L);
        assertThat(claims.impersonatorId()).isEqualTo(1L);
    }

    @Test
    void parseAccess_expiredToken_throwsJwtException() {
        String token = buildAccessToken(1L, "TEACHER", null, Instant.now().minusSeconds(1));

        assertThatThrownBy(() -> verifier.parseAccess(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void parseAccess_tamperedSignature_throwsJwtException() {
        String token = buildAccessToken(1L, "ADMIN", null, Instant.now().plusSeconds(900));
        String tampered = token.substring(0, token.length() - 4) + "XXXX";

        assertThatThrownBy(() -> verifier.parseAccess(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void parseAccess_refreshToken_throwsJwtExceptionForWrongType() {
        String refreshToken = Jwts.builder()
                .subject("7")
                .claim("typ", "refresh")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(secretKey)
                .compact();

        assertThatThrownBy(() -> verifier.parseAccess(refreshToken))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("access");
    }

    @Test
    void parseAccess_randomString_throwsJwtException() {
        assertThatThrownBy(() -> verifier.parseAccess("not.a.jwt"))
                .isInstanceOf(JwtException.class);
    }

    private String buildAccessToken(Long userId, String role, Long impersonatorId, Instant expiry) {
        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .claim("typ", "access")
                .claim("email", "user@example.com")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiry));
        if (impersonatorId != null) {
            builder.claim("imp", impersonatorId);
        }
        return builder.signWith(secretKey).compact();
    }
}

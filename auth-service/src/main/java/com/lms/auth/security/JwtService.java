package com.lms.auth.security;

import com.lms.auth.config.JwtProperties;
import com.lms.auth.entity.RoleName;
import com.lms.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    static final String CLAIM_TYPE = "typ";
    static final String CLAIM_ROLE = "role";
    static final String CLAIM_EMAIL = "email";
    static final String CLAIM_IMPERSONATOR = "imp";

    static final String TYPE_ACCESS = "access";
    static final String TYPE_REFRESH = "refresh";

    private final JwtProperties properties;
    private final SecretKey secretKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.secretKey = resolveKey(properties.getSecret());
    }

    private static SecretKey resolveKey(String secret) {
        byte[] raw = null;
        try {
            byte[] decoded = Decoders.BASE64.decode(secret);
            if (decoded.length >= 32) {
                raw = decoded;
            }
        } catch (IllegalArgumentException | DecodingException ignored) {
            // fall through to passphrase path
        }
        if (raw == null) {
            byte[] utf8 = secret.getBytes(StandardCharsets.UTF_8);
            raw = utf8.length >= 32 ? utf8 : sha256(utf8);
        }
        return Keys.hmacShaKeyFor(raw);
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public String createAccessToken(User user, Long impersonatorId) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(properties.getAccessExpirySeconds());
        var builder = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE, user.getRole().getName().name())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp));
        if (impersonatorId != null) {
            builder.claim(CLAIM_IMPERSONATOR, impersonatorId);
        }
        return builder.signWith(secretKey).compact();
    }

    public String createRefreshToken(User user) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(properties.getRefreshExpirySeconds());
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(secretKey)
                .compact();
    }

    public Claims parseAndValidate(String token) {
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
    }

    public JwtUserPrincipal toPrincipal(Claims claims, String expectedType) {
        if (!expectedType.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("Invalid token type");
        }
        Long userId = Long.parseLong(claims.getSubject());
        RoleName role = RoleName.valueOf(claims.get(CLAIM_ROLE, String.class));
        String email = claims.get(CLAIM_EMAIL, String.class);
        Long imp = claims.get(CLAIM_IMPERSONATOR, Long.class);
        return new JwtUserPrincipal(userId, email, role, imp);
    }

    public JwtUserPrincipal parseAccessPrincipal(String token) {
        Claims claims = parseAndValidate(token);
        return toPrincipal(claims, TYPE_ACCESS);
    }

    public Long parseRefreshUserId(String token) {
        Claims claims = parseAndValidate(token);
        if (!TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("Invalid token type");
        }
        return Long.parseLong(claims.getSubject());
    }

    public TokenPair issueTokens(User user, Long impersonatorId) {
        return new TokenPair(
                createAccessToken(user, impersonatorId),
                impersonatorId == null ? createRefreshToken(user) : null,
                properties.getAccessExpirySeconds());
    }

    public record TokenPair(String accessToken, String refreshToken, long accessExpiresInSeconds) {}
}

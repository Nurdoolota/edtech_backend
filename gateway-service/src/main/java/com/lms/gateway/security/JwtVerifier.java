package com.lms.gateway.security;

import com.lms.gateway.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * Stateless JWT verifier — reads and validates access tokens without calling any service.
 * Mirrors the resolveKey() logic from auth-service so both use the same HMAC key derivation.
 */
@Component
public class JwtVerifier {

    static final String CLAIM_TYPE = "typ";
    static final String CLAIM_ROLE = "role";
    static final String CLAIM_IMPERSONATOR = "imp";
    static final String TYPE_ACCESS = "access";

    private final SecretKey secretKey;

    public JwtVerifier(JwtProperties properties) {
        this.secretKey = resolveKey(properties.getSecret());
    }

    /**
     * Parse and validate an access token.
     *
     * @return {@link JwtClaims} with userId, role, and optional impersonatorId
     * @throws JwtException if the token is invalid, expired, or not an access token
     */
    public JwtClaims parseAccess(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("Invalid token type: expected access");
        }

        Long userId = Long.parseLong(claims.getSubject());
        String role = claims.get(CLAIM_ROLE, String.class);
        Long impersonatorId = claims.get(CLAIM_IMPERSONATOR, Long.class);

        return new JwtClaims(userId, role, impersonatorId);
    }

    /**
     * Derives the HMAC key from the secret string.
     * Identical to auth-service JwtService.resolveKey() — both must stay in sync.
     *
     * Priority:
     *  1. If the string is valid Base64 and decodes to >= 32 bytes → use decoded bytes directly.
     *  2. If UTF-8 bytes are >= 32 → use them directly.
     *  3. Otherwise → SHA-256 hash of UTF-8 bytes (guaranteed 32 bytes).
     */
    public static SecretKey resolveKey(String secret) {
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

    public record JwtClaims(Long userId, String role, Long impersonatorId) {}
}

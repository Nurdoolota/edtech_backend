package com.lms.content.security;

import com.lms.content.config.JwtProperties;
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
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_EMAIL = "email";
    private static final String TYPE_ACCESS = "access";

    private final SecretKey secretKey;

    public JwtService(JwtProperties properties) {
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

    public JwtUserPrincipal parseAccessPrincipal(String token) {
        Claims claims = Jwts.parser().verifyWith(secretKey).build()
                .parseSignedClaims(token).getPayload();
        if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("Invalid token type");
        }
        Long userId = Long.parseLong(claims.getSubject());
        RoleName role = RoleName.valueOf(claims.get(CLAIM_ROLE, String.class));
        String email = claims.get(CLAIM_EMAIL, String.class);
        return new JwtUserPrincipal(userId, email, role);
    }
}

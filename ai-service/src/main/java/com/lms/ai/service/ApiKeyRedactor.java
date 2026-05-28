package com.lms.ai.service;

import org.springframework.stereotype.Component;

/**
 * Redacts API key patterns from strings to prevent secret leakage in logs and DB records.
 *
 * <p>Patterns redacted:
 * <ul>
 *   <li>{@code Bearer <token>} → {@code Bearer [REDACTED]}
 *   <li>{@code sk-<token>} → {@code sk-[REDACTED]}
 * </ul>
 */
@Component
public class ApiKeyRedactor {

    private static final String BEARER_PATTERN = "Bearer [A-Za-z0-9_\\-.]+";
    private static final String BEARER_REPLACEMENT = "Bearer [REDACTED]";

    private static final String SK_PATTERN = "sk-[A-Za-z0-9]+";
    private static final String SK_REPLACEMENT = "sk-[REDACTED]";

    /**
     * Returns {@code null} when the input is {@code null}; otherwise replaces any API key
     * patterns in the string with redaction placeholders.
     */
    public String redact(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replaceAll(BEARER_PATTERN, BEARER_REPLACEMENT)
                .replaceAll(SK_PATTERN, SK_REPLACEMENT);
    }
}

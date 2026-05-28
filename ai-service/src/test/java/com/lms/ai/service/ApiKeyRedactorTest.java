package com.lms.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiKeyRedactorTest {

    private final ApiKeyRedactor redactor = new ApiKeyRedactor();

    @Test
    void redact_nullInput_returnsNull() {
        assertThat(redactor.redact(null)).isNull();
    }

    @Test
    void redact_noApiKey_unchanged() {
        String message = "Connection refused to localhost:1234";
        assertThat(redactor.redact(message)).isEqualTo(message);
    }

    @Test
    void redact_bearerToken_isRedacted() {
        String message = "Authorization: Bearer sk-abc123_XYZ.def-456";
        assertThat(redactor.redact(message))
                .isEqualTo("Authorization: Bearer [REDACTED]");
    }

    @Test
    void redact_skToken_isRedacted() {
        String message = "Auth failed: sk-abc123def456 is invalid";
        assertThat(redactor.redact(message))
                .isEqualTo("Auth failed: sk-[REDACTED] is invalid");
    }

    @Test
    void redact_bothPatterns_bothRedacted() {
        String message = "Bearer myToken123 and sk-secretkey999";
        String result = redactor.redact(message);
        assertThat(result).doesNotContain("myToken123");
        assertThat(result).doesNotContain("secretkey999");
        assertThat(result).contains("Bearer [REDACTED]");
        assertThat(result).contains("sk-[REDACTED]");
    }

    @Test
    void redact_emptyString_returnsEmpty() {
        assertThat(redactor.redact("")).isEqualTo("");
    }
}

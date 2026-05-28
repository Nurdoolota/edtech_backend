package com.lms.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.verify;

import com.lms.ai.entity.AiCallLog;
import com.lms.ai.repository.AiCallLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

@ExtendWith(MockitoExtension.class)
class AiCallLoggerTest {

    @Mock
    private AiCallLogRepository repository;

    private AiCallLogger logger;

    @BeforeEach
    void setUp() {
        logger = new AiCallLogger(repository, new ApiKeyRedactor());
    }

    // ------------------------------------------------------------------
    // Acceptance criterion: SUCCESS entry is persisted with all fields
    // ------------------------------------------------------------------

    @Test
    void log_successEntry_savesAllFieldsCorrectly() {
        AiCallEntry entry = new AiCallEntry(
                42L, "/internal/ai/evaluate", "llama-3.1-8b-instant",
                1832, 512, 1024, "SUCCESS", null);

        logger.log(entry);

        ArgumentCaptor<AiCallLog> captor = forClass(AiCallLog.class);
        verify(repository).save(captor.capture());

        AiCallLog saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(42L);
        assertThat(saved.getEndpoint()).isEqualTo("/internal/ai/evaluate");
        assertThat(saved.getModel()).isEqualTo("llama-3.1-8b-instant");
        assertThat(saved.getLatencyMs()).isEqualTo(1832);
        assertThat(saved.getTokensIn()).isEqualTo(512);
        assertThat(saved.getTokensOut()).isEqualTo(1024);
        assertThat(saved.getStatus()).isEqualTo("SUCCESS");
        assertThat(saved.getError()).isNull();
    }

    // ------------------------------------------------------------------
    // Acceptance criterion: Bearer token in error field is redacted
    // ------------------------------------------------------------------

    @Test
    void log_errorWithBearerToken_redactsApiKey() {
        AiCallEntry entry = new AiCallEntry(
                1L, "/internal/ai/evaluate", "llama-3.1-8b-instant",
                100, null, null, "ERROR",
                "Request failed with Bearer sk-secret123abc in header");

        logger.log(entry);

        ArgumentCaptor<AiCallLog> captor = forClass(AiCallLog.class);
        verify(repository).save(captor.capture());

        AiCallLog saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("ERROR");
        assertThat(saved.getError()).doesNotContain("sk-secret123abc");
        assertThat(saved.getError()).contains("[REDACTED]");
    }

    // ------------------------------------------------------------------
    // Acceptance criterion: sk- style token in error field is redacted
    // ------------------------------------------------------------------

    @Test
    void log_errorWithSkToken_redactsApiKey() {
        AiCallEntry entry = new AiCallEntry(
                1L, "/internal/ai/evaluate", "gpt-4o",
                200, null, null, "ERROR",
                "Auth failed: sk-abc123def456 is invalid");

        logger.log(entry);

        ArgumentCaptor<AiCallLog> captor = forClass(AiCallLog.class);
        verify(repository).save(captor.capture());

        AiCallLog saved = captor.getValue();
        assertThat(saved.getError()).doesNotContain("sk-abc123def456");
        assertThat(saved.getError()).contains("sk-[REDACTED]");
    }

    // ------------------------------------------------------------------
    // Acceptance criterion: requestId is read from MDC
    // ------------------------------------------------------------------

    @Test
    void log_requestIdSetInMdc_populatesRequestId() {
        MDC.put("requestId", "test-request-id-123");
        try {
            AiCallEntry entry = new AiCallEntry(
                    5L, "/internal/ai/evaluate", "local-model",
                    500, 100, 200, "SUCCESS", null);

            logger.log(entry);

            ArgumentCaptor<AiCallLog> captor = forClass(AiCallLog.class);
            verify(repository).save(captor.capture());

            assertThat(captor.getValue().getRequestId()).isEqualTo("test-request-id-123");
        } finally {
            MDC.remove("requestId");
        }
    }

    // ------------------------------------------------------------------
    // Acceptance criterion: requestId is null when MDC not set
    // ------------------------------------------------------------------

    @Test
    void log_noMdcRequestId_requestIdIsNull() {
        MDC.remove("requestId");

        AiCallEntry entry = new AiCallEntry(
                null, "/internal/ai/evaluate", "local-model",
                300, null, null, "TIMEOUT", "timeout");

        logger.log(entry);

        ArgumentCaptor<AiCallLog> captor = forClass(AiCallLog.class);
        verify(repository).save(captor.capture());

        assertThat(captor.getValue().getRequestId()).isNull();
    }

    // ------------------------------------------------------------------
    // Acceptance criterion: null error stays null (no NPE in redactor)
    // ------------------------------------------------------------------

    @Test
    void log_nullError_savedAsNull() {
        AiCallEntry entry = new AiCallEntry(
                7L, "/internal/ai/health", "local-model",
                50, null, null, "SUCCESS", null);

        logger.log(entry);

        ArgumentCaptor<AiCallLog> captor = forClass(AiCallLog.class);
        verify(repository).save(captor.capture());

        assertThat(captor.getValue().getError()).isNull();
    }
}

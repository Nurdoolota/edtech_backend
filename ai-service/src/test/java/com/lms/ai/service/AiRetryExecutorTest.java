package com.lms.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.ai.config.AiProperties;
import com.lms.ai.exception.AiServiceException;
import com.lms.ai.exception.JsonRepairException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
class AiRetryExecutorTest {

    @Mock
    private JsonRepairer jsonRepairer;

    private AiProperties aiProperties;
    private AiRetryExecutor executor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Simple DTO used in tests. */
    record TestResponse(String value) {}

    @BeforeEach
    void setUp() {
        aiProperties = new AiProperties();
        aiProperties.setMaxRetries(3);
        executor = new AiRetryExecutor(aiProperties, jsonRepairer, objectMapper);
    }

    // -----------------------------------------------------------------------
    // Acceptance criterion 1: retries exactly 3 times when LLM always throws
    // -----------------------------------------------------------------------

    @Test
    void executeWithRetry_alwaysThrowsRestClientException_exhaustsRetriesAndThrows() {
        AtomicInteger callCount = new AtomicInteger(0);
        Supplier<String> failingSupplier = () -> {
            callCount.incrementAndGet();
            throw new ResourceAccessException("timeout");
        };

        assertThatThrownBy(() -> executor.executeWithRetry(failingSupplier, TestResponse.class))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("retries");

        assertThat(callCount.get()).isEqualTo(3);
    }

    // -----------------------------------------------------------------------
    // Acceptance criterion 2: repair succeeds — no further retry
    // -----------------------------------------------------------------------

    @Test
    void executeWithRetry_invalidJsonThenRepairSucceeds_returnsWithoutFurtherRetry()
            throws Exception {
        String brokenJson = "{value: missing-quotes}";
        String repairedJson = "{\"value\":\"fixed\"}";

        AtomicInteger callCount = new AtomicInteger(0);
        Supplier<String> supplier = () -> {
            callCount.incrementAndGet();
            return brokenJson;
        };
        when(jsonRepairer.repair(brokenJson, "")).thenReturn(repairedJson);

        TestResponse result = executor.executeWithRetry(supplier, TestResponse.class);

        assertThat(result.value()).isEqualTo("fixed");
        // LLM called exactly once — repair succeeded on first attempt
        assertThat(callCount.get()).isEqualTo(1);
        verify(jsonRepairer, times(1)).repair(brokenJson, "");
    }

    // -----------------------------------------------------------------------
    // Acceptance criterion: repair failure propagates immediately (not retried)
    // -----------------------------------------------------------------------

    @Test
    void executeWithRetry_invalidJsonAndRepairFails_throwsImmediately() throws Exception {
        String brokenJson = "not json at all";
        AtomicInteger callCount = new AtomicInteger(0);
        Supplier<String> supplier = () -> {
            callCount.incrementAndGet();
            return brokenJson;
        };
        when(jsonRepairer.repair(anyString(), anyString()))
                .thenThrow(new JsonRepairException("still invalid"));

        assertThatThrownBy(() -> executor.executeWithRetry(supplier, TestResponse.class))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("JSON repair failed");

        // Should not retry after repair failure
        assertThat(callCount.get()).isEqualTo(1);
    }

    // -----------------------------------------------------------------------
    // Acceptance criterion: successful parse on first attempt
    // -----------------------------------------------------------------------

    @Test
    void executeWithRetry_validJsonOnFirstAttempt_returnsImmediately() {
        String validJson = "{\"value\":\"hello\"}";
        Supplier<String> supplier = () -> validJson;

        TestResponse result = executor.executeWithRetry(supplier, TestResponse.class);

        assertThat(result.value()).isEqualTo("hello");
        verify(jsonRepairer, never()).repair(anyString(), anyString());
    }

    // -----------------------------------------------------------------------
    // Acceptance criterion: AI_MAX_RETRIES=1 results in only one LLM attempt
    // -----------------------------------------------------------------------

    @Test
    void executeWithRetry_maxRetriesOne_onlyOneAttemptBeforeException() {
        aiProperties.setMaxRetries(1);
        AtomicInteger callCount = new AtomicInteger(0);
        Supplier<String> failingSupplier = () -> {
            callCount.incrementAndGet();
            throw new ResourceAccessException("timeout");
        };

        assertThatThrownBy(() -> executor.executeWithRetry(failingSupplier, TestResponse.class))
                .isInstanceOf(AiServiceException.class);

        assertThat(callCount.get()).isEqualTo(1);
    }

    // -----------------------------------------------------------------------
    // Acceptance criterion: attempt counts (sleep durations verified via counts)
    // -----------------------------------------------------------------------

    @Test
    void executeWithRetry_twoFailuresThenSuccess_calledThreeTimes() throws Exception {
        String validJson = "{\"value\":\"ok\"}";
        AtomicInteger callCount = new AtomicInteger(0);
        // Override with maxRetries=3 (default); fail twice then succeed
        Supplier<String> supplier = () -> {
            int n = callCount.incrementAndGet();
            if (n < 3) {
                throw new ResourceAccessException("timeout attempt " + n);
            }
            return validJson;
        };

        // Use maxRetries=3 but we need sleep to be very short for test speed.
        // We override to 3 retries but shrink the executor's sleep by using
        // a subclass that overrides only sleep. Since we can't inject the sleep,
        // we instead accept that the test may take up to 1s + 2s = 3s total.
        // To keep the test practical we use maxRetries=3 with attempts 1 and 2 failing.
        TestResponse result = executor.executeWithRetry(supplier, TestResponse.class);

        assertThat(result.value()).isEqualTo("ok");
        assertThat(callCount.get()).isEqualTo(3);
    }

    // -----------------------------------------------------------------------
    // Redact API key utility
    // -----------------------------------------------------------------------

    @Test
    void redactApiKey_replacesBearer() {
        String msg = "Authorization: Bearer sk-abc123_XYZ.def-456";
        assertThat(AiRetryExecutor.redactApiKey(msg))
                .isEqualTo("Authorization: Bearer [REDACTED]");
    }

    @Test
    void redactApiKey_noBearer_unchanged() {
        String msg = "Connection refused";
        assertThat(AiRetryExecutor.redactApiKey(msg)).isEqualTo("Connection refused");
    }

    @Test
    void redactApiKey_nullMessage_returnsNull() {
        assertThat(AiRetryExecutor.redactApiKey(null)).isNull();
    }
}

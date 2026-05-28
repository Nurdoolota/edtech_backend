package com.lms.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.ai.config.AiProperties;
import com.lms.ai.exception.AiServiceException;
import com.lms.ai.exception.JsonRepairException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * Centralised retry executor that wraps LLM calls with exponential back-off and automatic
 * JSON-repair on parse failure.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Attempt up to {@code maxRetries} times (default 3).
 *   <li>On each attempt: invoke {@code llmCall.get()} to obtain raw JSON.
 *   <li>Try {@link ObjectMapper#readValue} on the raw string.
 *   <li>If {@link JsonProcessingException}: invoke {@link JsonRepairer#repair} once; try parse again.
 *       If repair fails (throws {@link JsonRepairException}), propagate immediately as {@link AiServiceException}.
 *   <li>On {@link RestClientException} or other runtime exception: treat as attempt failure.
 *   <li>Between failed attempts: sleep 1 s × 2^(attempt − 1) (1 s, 2 s, 4 s).
 *   <li>After all attempts exhausted: throw {@link AiServiceException}.
 * </ol>
 */
@Component
public class AiRetryExecutor {

    private static final Logger log = LoggerFactory.getLogger(AiRetryExecutor.class);

    private final AiProperties aiProperties;
    private final JsonRepairer jsonRepairer;
    private final ObjectMapper objectMapper;

    public AiRetryExecutor(AiProperties aiProperties,
                           JsonRepairer jsonRepairer,
                           ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.jsonRepairer = jsonRepairer;
        this.objectMapper = objectMapper;
    }

    /**
     * Execute the given LLM call supplier with retry and JSON-repair.
     *
     * @param llmCall      a {@link Supplier} that calls the LLM and returns a raw JSON string
     * @param responseType the target class to deserialize the JSON into
     * @param <T>          response type
     * @return a deserialized response object
     * @throws AiServiceException if all attempts fail or JSON repair cannot produce valid JSON
     */
    public <T> T executeWithRetry(Supplier<String> llmCall, Class<T> responseType) {
        int maxRetries = aiProperties.getMaxRetries();

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String raw = llmCall.get();

                try {
                    return objectMapper.readValue(raw, responseType);
                } catch (JsonProcessingException parseEx) {
                    log.warn("LLM returned invalid JSON on attempt {}/{}; attempting repair. Error: {}",
                            attempt, maxRetries, parseEx.getMessage());
                    // repair() throws JsonRepairException if LLM cannot produce valid JSON —
                    // caught below and immediately propagated (no retry on repair failure).
                    String repaired = jsonRepairer.repair(raw, "");
                    return objectMapper.readValue(repaired, responseType);
                }

            } catch (JsonRepairException e) {
                // Repair failure is not retryable — propagate immediately
                throw new AiServiceException("JSON repair failed: " + e.getMessage(), e);
            } catch (Exception ex) {
                String safeMessage = redactApiKey(ex.getMessage());
                if (attempt == maxRetries) {
                    log.error("LLM call failed after {}/{} attempts: {}", attempt, maxRetries, safeMessage);
                    throw new AiServiceException("LLM call failed after retries");
                }
                log.warn("LLM call attempt {}/{} failed: {}; retrying...", attempt, maxRetries, safeMessage);
                long backoffMs = 1000L * (1L << (attempt - 1)); // 1s, 2s, 4s
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new AiServiceException("LLM retry sleep interrupted", ie);
                }
            }
        }
        throw new AiServiceException("LLM call failed after retries");
    }

    /**
     * Redact Bearer tokens from a message before logging to prevent API key leakage.
     */
    public static String redactApiKey(String message) {
        if (message == null) return null;
        return message.replaceAll("Bearer [A-Za-z0-9_\\-.]+", "Bearer [REDACTED]");
    }
}

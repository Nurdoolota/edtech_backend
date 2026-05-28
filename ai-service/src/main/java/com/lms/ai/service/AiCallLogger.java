package com.lms.ai.service;

import com.lms.ai.entity.AiCallLog;
import com.lms.ai.repository.AiCallLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists one {@link AiCallLog} row after every LLM call (success and failure).
 *
 * <p>The {@code X-Request-Id} value is read from {@link MDC} (key {@code "requestId"}) which is
 * populated for every request by {@link com.lms.ai.config.RequestIdFilter}.
 */
@Component
public class AiCallLogger {

    private static final Logger log = LoggerFactory.getLogger(AiCallLogger.class);

    private final AiCallLogRepository repository;
    private final ApiKeyRedactor apiKeyRedactor;

    public AiCallLogger(AiCallLogRepository repository, ApiKeyRedactor apiKeyRedactor) {
        this.repository = repository;
        this.apiKeyRedactor = apiKeyRedactor;
    }

    /**
     * Writes a row to {@code ai_call_log}.
     * API keys in the {@code error} field are redacted before persistence.
     * Never throws — logging failures must not propagate to callers.
     */
    @Transactional
    public void log(AiCallEntry entry) {
        try {
            AiCallLog record = new AiCallLog();
            record.setRequestId(MDC.get("requestId"));
            record.setUserId(entry.userId());
            record.setEndpoint(entry.endpoint());
            record.setModel(entry.model());
            record.setLatencyMs(entry.latencyMs());
            record.setTokensIn(entry.tokensIn());
            record.setTokensOut(entry.tokensOut());
            record.setStatus(entry.status());
            record.setError(apiKeyRedactor.redact(entry.error()));
            repository.save(record);
        } catch (Exception ex) {
            log.error("Failed to persist AI call log entry: {}", ex.getMessage(), ex);
        }
    }
}

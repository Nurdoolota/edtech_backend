package com.lms.ai.dto;

import java.time.Instant;

/**
 * Single row in the AI call log response. Field names match AUTH-07 {@code AiCallLogResponse}
 * exactly so that the auth-service proxy can deserialize this without any mapping.
 */
public record AiCallLogResponse(
        Long id,
        String requestId,
        Long userId,
        String endpoint,
        String model,
        int latencyMs,
        Integer tokensIn,
        Integer tokensOut,
        String status,
        String error,
        Instant createdAt
) {
}

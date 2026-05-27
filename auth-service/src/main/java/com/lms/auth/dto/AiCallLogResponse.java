package com.lms.auth.dto;

import java.time.Instant;

public record AiCallLogResponse(
        Long id,
        String requestId,
        Long userId,
        String endpoint,
        String model,
        Integer latencyMs,
        Integer tokensIn,
        Integer tokensOut,
        String status,
        String error,
        Instant createdAt
) {}

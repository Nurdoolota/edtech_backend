package com.lms.ai.dto;

import java.util.List;

/**
 * Paginated wrapper returned by {@code GET /internal/ai/log}.
 * The shape must match AUTH-07 {@code PagedAiLogResponse} exactly.
 */
public record PagedAiLogResponse(
        List<AiCallLogResponse> content,
        long totalElements,
        int totalPages,
        int page,
        int size
) {
}

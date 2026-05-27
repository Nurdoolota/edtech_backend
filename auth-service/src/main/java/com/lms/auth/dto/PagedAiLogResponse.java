package com.lms.auth.dto;

import java.util.List;

public record PagedAiLogResponse(
        List<AiCallLogResponse> content,
        long totalElements,
        int totalPages,
        int page,
        int size
) {}

package com.lms.content.dto.block;

import java.time.Instant;

public record BlockResponse(
        Long id,
        Long lessonId,
        int orderIndex,
        String type,
        String contentJson,
        boolean aiGenerated,
        Instant createdAt
) {}

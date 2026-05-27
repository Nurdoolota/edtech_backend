package com.lms.content.dto.topic;

import java.time.Instant;

public record TopicResponse(
        Long id,
        Long courseId,
        String title,
        String description,
        int orderIndex,
        Integer globalOrder,
        Instant createdAt
) {}

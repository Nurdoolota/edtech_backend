package com.lms.content.dto.lesson;

import java.time.Instant;

public record LessonResponse(
        Long id,
        Long courseId,
        Long topicId,
        int orderIndex,
        Integer globalOrder,
        String title,
        String status,
        String publishMode,
        String unlockMode,
        boolean visible,
        int blocksCount,
        int tasksCount,
        Instant createdAt,
        Instant publishedAt
) {}

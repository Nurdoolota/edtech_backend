package com.lms.content.dto.task;

import com.lms.content.entity.TaskType;
import java.time.Instant;
import java.util.Map;

public record TaskResponse(
        Long id,
        Long courseId,
        Long lessonId,
        TaskType type,
        String title,
        Map<String, Object> content,
        String status,
        int orderIndex,
        String unlockMode,
        Long prerequisiteTaskId,
        Integer requiredScore,
        boolean aiGenerated,
        Long promptTemplateId,
        Instant createdAt,
        Instant updatedAt
) {}

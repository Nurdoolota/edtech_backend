package com.lms.content.dto.task;

import com.lms.content.entity.TaskType;
import java.time.Instant;
import java.util.Map;

public record TaskResponse(
        Long id,
        Long courseId,
        TaskType type,
        Map<String, Object> content,
        Long promptTemplateId,
        Instant createdAt,
        Instant updatedAt) {}

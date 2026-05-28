package com.lms.content.dto.task;

import com.lms.content.entity.TaskType;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record LessonTaskRequest(
        @NotNull TaskType type,
        @NotNull Map<String, Object> content,
        String title,
        String unlockMode,
        Long prerequisiteTaskId,
        Integer requiredScore,
        Long promptTemplateId
) {}

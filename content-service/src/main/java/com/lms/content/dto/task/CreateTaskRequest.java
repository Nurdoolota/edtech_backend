package com.lms.content.dto.task;

import com.lms.content.entity.TaskType;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record CreateTaskRequest(
        @NotNull Long courseId,
        @NotNull TaskType type,
        @NotNull Map<String, Object> content,
        Long promptTemplateId) {}

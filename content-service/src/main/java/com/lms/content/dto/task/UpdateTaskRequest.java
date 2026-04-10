package com.lms.content.dto.task;

import com.lms.content.entity.TaskType;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record UpdateTaskRequest(
        @NotNull TaskType type,
        @NotNull Map<String, Object> content,
        Long promptTemplateId) {}

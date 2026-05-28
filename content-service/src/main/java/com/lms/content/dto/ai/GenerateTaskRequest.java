package com.lms.content.dto.ai;

import jakarta.validation.constraints.NotNull;

public record GenerateTaskRequest(
        @NotNull Long lessonId,
        String type,
        String context,
        String level
) {}

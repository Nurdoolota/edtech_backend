package com.lms.ai.dto.gen;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AiGenerateTaskRequest(
        @NotNull Long lessonId,
        @NotBlank String type,
        String context,
        @NotBlank String level
) {}

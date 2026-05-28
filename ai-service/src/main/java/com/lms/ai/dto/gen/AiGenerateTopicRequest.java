package com.lms.ai.dto.gen;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AiGenerateTopicRequest(
        @NotNull Long courseId,
        @NotBlank String topicTitle,
        String description,
        @NotBlank String level,
        @NotNull Integer lessonCount
) {}

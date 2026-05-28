package com.lms.ai.dto.gen;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AiGenerateLessonRequest(
        @NotNull Long courseId,
        @NotNull Long topicId,
        @NotBlank String topic,
        @NotBlank String level,
        String length,
        List<String> taskTypes,
        Boolean includeTheory,
        String instructions
) {}

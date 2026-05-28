package com.lms.ai.dto.gen;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record AiRegenerateLessonRequest(
        @NotBlank String lessonJson,
        String hint,
        List<Long> preserveIds,
        List<String> taskTypes
) {}

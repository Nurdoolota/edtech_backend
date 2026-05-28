package com.lms.content.dto.ai;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record GenerateLessonRequest(
        @NotNull Long courseId,
        Long topicId,
        String topic,
        String level,
        String length,
        List<String> taskTypes,
        Boolean includeTheory,
        String instructions
) {}

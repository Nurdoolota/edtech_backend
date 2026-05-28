package com.lms.content.client.dto;

import java.util.List;

public record AiRegenerateLessonRequest(
        Long lessonId,
        String existingLessonJson,
        String hint,
        List<Long> preserveIds,
        List<String> taskTypes
) {}

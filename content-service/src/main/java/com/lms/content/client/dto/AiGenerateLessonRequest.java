package com.lms.content.client.dto;

import java.util.List;

public record AiGenerateLessonRequest(
        Long courseId,
        Long topicId,
        String topic,
        String level,
        String length,
        List<String> taskTypes,
        Boolean includeTheory,
        String instructions
) {}

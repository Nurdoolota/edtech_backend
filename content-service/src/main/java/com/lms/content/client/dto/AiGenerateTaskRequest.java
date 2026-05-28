package com.lms.content.client.dto;

public record AiGenerateTaskRequest(
        Long lessonId,
        String type,
        String context,
        String level
) {}

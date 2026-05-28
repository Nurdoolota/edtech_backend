package com.lms.content.client.dto;

public record AiGenerateTopicRequest(
        Long courseId,
        String topicTitle,
        String description,
        String level,
        Integer lessonCount
) {}

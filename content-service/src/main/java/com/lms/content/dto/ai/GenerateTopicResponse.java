package com.lms.content.dto.ai;

public record GenerateTopicResponse(
        Long topicId,
        String topicTitle,
        int lessonsCreated,
        int totalTasks
) {}

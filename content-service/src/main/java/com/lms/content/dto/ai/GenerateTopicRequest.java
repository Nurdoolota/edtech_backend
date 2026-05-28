package com.lms.content.dto.ai;

import jakarta.validation.constraints.NotNull;

public record GenerateTopicRequest(
        @NotNull Long courseId,
        String topicTitle,
        String description,
        String level,
        Integer lessonCount
) {}

package com.lms.content.client.dto;

import java.util.List;

public record AiTopicJson(
        String topicTitle,
        List<AiLessonJson> lessons
) {}

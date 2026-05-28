package com.lms.ai.dto.gen;

import java.util.List;

public record AiTopicJson(
        String topicTitle,
        List<AiLessonJson> lessons
) {}

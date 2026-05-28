package com.lms.content.client.dto;

import java.util.List;
import java.util.Map;

public record AiLessonJson(
        String title,
        String generationMetadata,
        List<AiBlockJson> blocks,
        List<AiTaskJson> tasks
) {

    public record AiBlockJson(
            String type,
            String contentJson,
            int orderIndex
    ) {}

    public record AiTaskJson(
            String type,
            String title,
            Map<String, Object> content,
            int orderIndex,
            String unlockMode
    ) {}
}

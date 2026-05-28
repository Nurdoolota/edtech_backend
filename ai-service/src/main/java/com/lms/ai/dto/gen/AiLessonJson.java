package com.lms.ai.dto.gen;

import java.util.List;

public record AiLessonJson(
        String title,
        String generationMetadata,
        List<AiBlockJson> blocks,
        List<AiTaskJson> tasks
) {}

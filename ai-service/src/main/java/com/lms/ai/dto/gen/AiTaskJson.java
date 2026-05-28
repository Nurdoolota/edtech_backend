package com.lms.ai.dto.gen;

import com.fasterxml.jackson.databind.JsonNode;

public record AiTaskJson(
        String type,
        String title,
        JsonNode content,
        Integer orderIndex,
        String unlockMode
) {}

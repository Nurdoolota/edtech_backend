package com.lms.content.client.dto;

import java.util.Map;

public record AiTaskJson(
        String type,
        String title,
        Map<String, Object> content,
        int orderIndex,
        String unlockMode
) {}

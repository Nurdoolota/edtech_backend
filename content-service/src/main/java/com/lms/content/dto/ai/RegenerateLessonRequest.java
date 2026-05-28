package com.lms.content.dto.ai;

import java.util.List;

public record RegenerateLessonRequest(
        String hint,
        List<Long> preserveIds,
        List<String> taskTypes
) {}

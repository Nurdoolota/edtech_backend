package com.lms.content.dto;

import java.util.List;

public record ImportResultResponse(
    Long courseId,
    int importedTopics,
    int importedLessons,
    int importedTasks,
    List<String> warnings
) {}

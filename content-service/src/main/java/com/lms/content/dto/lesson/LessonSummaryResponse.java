package com.lms.content.dto.lesson;

public record LessonSummaryResponse(
        Long id,
        String title,
        String status,
        int orderIndex
) {}

package com.lms.content.dto.student;

import java.time.Instant;

/**
 * Student-facing course list item: includes progress metrics from learning-service.
 */
public record StudentCourseResponse(
        Long id,
        String title,
        String description,
        String level,
        String accessStatus,
        int lessonCount,
        int completedLessons,
        double progressPercent,
        Instant createdAt) {}

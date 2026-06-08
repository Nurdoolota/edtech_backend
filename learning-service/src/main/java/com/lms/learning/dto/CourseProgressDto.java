package com.lms.learning.dto;

public record CourseProgressDto(
        Long courseId,
        int totalTasks,
        int completedTasks,
        int totalLessons,
        int completedLessons,
        double progressPercent,
        Double averageScore) {}

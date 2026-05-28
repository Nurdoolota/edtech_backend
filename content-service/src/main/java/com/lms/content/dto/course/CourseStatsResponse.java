package com.lms.content.dto.course;

public record CourseStatsResponse(
        int topicsCount,
        int lessonsCount,
        int tasksCount,
        int publishedLessons,
        int draftLessons,
        int aiGeneratedLessons,
        int studentsAssigned) {}

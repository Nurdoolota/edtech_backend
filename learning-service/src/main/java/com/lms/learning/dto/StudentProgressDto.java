package com.lms.learning.dto;

import java.util.List;

public record StudentProgressDto(
        int streakDays,
        int dailyGoalMinutes,
        int todayMinutesEstimate,
        int todayTasksCompleted,
        int weekLessonsCompleted,
        int weekTasksCompleted,
        List<DailyStatDto> last7Days) {

    public record DailyStatDto(
            String date,
            int tasksCompleted,
            int minutesEstimate) {}
}

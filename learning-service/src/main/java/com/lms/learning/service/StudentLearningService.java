package com.lms.learning.service;

import com.lms.learning.dto.CourseProgressDto;
import com.lms.learning.dto.StudentProgressDto;
import com.lms.learning.dto.StudentProgressDto.DailyStatDto;
import com.lms.learning.entity.TaskResult;
import com.lms.learning.repository.TaskRepository;
import com.lms.learning.repository.TaskResultRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StudentLearningService {

    /** Average task duration in minutes used to estimate time spent. */
    private static final int MINUTES_PER_TASK = 5;

    private final TaskResultRepository taskResultRepository;
    private final TaskRepository taskRepository;

    public StudentLearningService(TaskResultRepository taskResultRepository,
            TaskRepository taskRepository) {
        this.taskResultRepository = taskResultRepository;
        this.taskRepository = taskRepository;
    }

    public StudentProgressDto getProgress(Long studentId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant startOfToday = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant sevenDaysAgo = today.minusDays(6).atStartOfDay(ZoneOffset.UTC).toInstant();

        // All results from last 7 days
        List<TaskResult> recentResults = taskResultRepository.findByStudentIdSince(studentId, sevenDaysAgo);

        // Today's completed tasks
        long todayTasks = recentResults.stream()
                .filter(r -> !r.getCreatedAt().isBefore(startOfToday))
                .count();

        // Week tasks completed (last 7 days)
        long weekTasks = recentResults.size();

        // Streak calculation: count consecutive days with at least one submission (backwards from today)
        int streak = computeStreak(recentResults, today);

        // Daily breakdown for last 7 days
        Map<LocalDate, List<TaskResult>> byDay = recentResults.stream()
                .collect(Collectors.groupingBy(r ->
                        r.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate()));

        List<DailyStatDto> last7Days = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            List<TaskResult> dayResults = byDay.getOrDefault(date, List.of());
            int count = dayResults.size();
            last7Days.add(new DailyStatDto(
                    date.format(fmt),
                    count,
                    count * MINUTES_PER_TASK));
        }

        return new StudentProgressDto(
                streak,
                30, // default daily goal minutes
                (int) todayTasks * MINUTES_PER_TASK,
                (int) todayTasks,
                0, // weekLessonsCompleted — lessons API is content-side, stub as 0
                (int) weekTasks,
                last7Days);
    }

    public CourseProgressDto getCourseProgress(Long studentId, Long courseId) {
        long totalTasks = taskRepository.countByCourseId(courseId);
        long completedTasks = taskResultRepository.countCompletedTasksByCourse(studentId, courseId);
        Double averageScore = taskResultRepository.findAverageScoreByCourse(studentId, courseId);

        double progressPercent = totalTasks > 0
                ? Math.round((completedTasks * 100.0 / totalTasks) * 10) / 10.0
                : 0.0;

        return new CourseProgressDto(
                courseId,
                (int) totalTasks,
                (int) completedTasks,
                0, // totalLessons — lessons are content-service data
                0, // completedLessons — lessons are content-service data
                progressPercent,
                averageScore);
    }

    private int computeStreak(List<TaskResult> results, LocalDate today) {
        if (results.isEmpty()) return 0;

        // Collect distinct days that have submissions
        var activeDays = results.stream()
                .map(r -> r.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate())
                .collect(Collectors.toSet());

        int streak = 0;
        LocalDate cursor = today;
        while (activeDays.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }
}

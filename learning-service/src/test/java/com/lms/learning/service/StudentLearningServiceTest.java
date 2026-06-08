package com.lms.learning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.lms.learning.dto.CourseProgressDto;
import com.lms.learning.dto.StudentProgressDto;
import com.lms.learning.entity.ResultStatus;
import com.lms.learning.entity.TaskResult;
import com.lms.learning.repository.TaskRepository;
import com.lms.learning.repository.TaskResultRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudentLearningServiceTest {

    @Mock
    TaskResultRepository taskResultRepository;

    @Mock
    TaskRepository taskRepository;

    StudentLearningService service;

    @BeforeEach
    void setUp() {
        service = new StudentLearningService(taskResultRepository, taskRepository);
    }

    // ── getProgress ───────────────────────────────────────────────────────────

    @Test
    void getProgress_noResults_returnsZeroStreak() {
        when(taskResultRepository.findByStudentIdSince(eq(1L), any(Instant.class)))
                .thenReturn(List.of());

        StudentProgressDto dto = service.getProgress(1L);

        assertThat(dto.streakDays()).isEqualTo(0);
        assertThat(dto.todayTasksCompleted()).isEqualTo(0);
        assertThat(dto.weekTasksCompleted()).isEqualTo(0);
        assertThat(dto.last7Days()).hasSize(7);
        assertThat(dto.dailyGoalMinutes()).isEqualTo(30);
    }

    @Test
    void getProgress_todayResult_returnsStreak1AndTodayCount1() {
        TaskResult r = makeResult(1L, 1L, Instant.now());
        when(taskResultRepository.findByStudentIdSince(eq(1L), any(Instant.class)))
                .thenReturn(List.of(r));

        StudentProgressDto dto = service.getProgress(1L);

        assertThat(dto.streakDays()).isEqualTo(1);
        assertThat(dto.todayTasksCompleted()).isEqualTo(1);
        assertThat(dto.todayMinutesEstimate()).isEqualTo(5); // 1 * MINUTES_PER_TASK
        assertThat(dto.weekTasksCompleted()).isEqualTo(1);
        assertThat(dto.last7Days()).hasSize(7);
        // last element is today
        StudentProgressDto.DailyStatDto today = dto.last7Days().get(6);
        assertThat(today.tasksCompleted()).isEqualTo(1);
        assertThat(today.minutesEstimate()).isEqualTo(5);
    }

    @Test
    void getProgress_multipleResultsToday_countsAllAsToday() {
        Instant now = Instant.now();
        List<TaskResult> results = List.of(
                makeResult(1L, 1L, now),
                makeResult(1L, 2L, now),
                makeResult(1L, 3L, now));
        when(taskResultRepository.findByStudentIdSince(eq(1L), any(Instant.class)))
                .thenReturn(results);

        StudentProgressDto dto = service.getProgress(1L);

        assertThat(dto.todayTasksCompleted()).isEqualTo(3);
        assertThat(dto.weekTasksCompleted()).isEqualTo(3);
        assertThat(dto.streakDays()).isEqualTo(1);
    }

    @Test
    void getProgress_last7DaysDates_areFormattedCorrectly() {
        when(taskResultRepository.findByStudentIdSince(eq(1L), any(Instant.class)))
                .thenReturn(List.of());

        StudentProgressDto dto = service.getProgress(1L);

        // Verify date format yyyy-MM-dd
        String firstDate = dto.last7Days().get(0).date();
        assertThat(firstDate).matches("\d{4}-\d{2}-\d{2}");
        String lastDate = dto.last7Days().get(6).date();
        assertThat(lastDate).isEqualTo(LocalDate.now(ZoneOffset.UTC).toString());
    }

    @Test
    void getProgress_weekLessonsCompleted_isAlwaysZeroStub() {
        when(taskResultRepository.findByStudentIdSince(eq(1L), any(Instant.class)))
                .thenReturn(List.of());

        StudentProgressDto dto = service.getProgress(1L);

        // weekLessonsCompleted is a stub (0) because lessons are content-side data
        assertThat(dto.weekLessonsCompleted()).isEqualTo(0);
    }

    // ── getCourseProgress ─────────────────────────────────────────────────────

    @Test
    void getCourseProgress_noTasksNorResults_returnsZeroes() {
        when(taskRepository.countByCourseId(42L)).thenReturn(0L);
        when(taskResultRepository.countCompletedTasksByCourse(1L, 42L)).thenReturn(0L);
        when(taskResultRepository.findAverageScoreByCourse(1L, 42L)).thenReturn(null);

        CourseProgressDto dto = service.getCourseProgress(1L, 42L);

        assertThat(dto.courseId()).isEqualTo(42L);
        assertThat(dto.totalTasks()).isEqualTo(0);
        assertThat(dto.completedTasks()).isEqualTo(0);
        assertThat(dto.progressPercent()).isEqualTo(0.0);
        assertThat(dto.averageScore()).isNull();
    }

    @Test
    void getCourseProgress_someCompleted_returnsCorrectPercent() {
        when(taskRepository.countByCourseId(42L)).thenReturn(10L);
        when(taskResultRepository.countCompletedTasksByCourse(1L, 42L)).thenReturn(7L);
        when(taskResultRepository.findAverageScoreByCourse(1L, 42L)).thenReturn(85.5);

        CourseProgressDto dto = service.getCourseProgress(1L, 42L);

        assertThat(dto.totalTasks()).isEqualTo(10);
        assertThat(dto.completedTasks()).isEqualTo(7);
        assertThat(dto.progressPercent()).isEqualTo(70.0);
        assertThat(dto.averageScore()).isEqualTo(85.5);
    }

    @Test
    void getCourseProgress_allCompleted_returns100Percent() {
        when(taskRepository.countByCourseId(42L)).thenReturn(5L);
        when(taskResultRepository.countCompletedTasksByCourse(1L, 42L)).thenReturn(5L);
        when(taskResultRepository.findAverageScoreByCourse(1L, 42L)).thenReturn(100.0);

        CourseProgressDto dto = service.getCourseProgress(1L, 42L);

        assertThat(dto.progressPercent()).isEqualTo(100.0);
    }

    @Test
    void getCourseProgress_totalLessons_areStubZero() {
        when(taskRepository.countByCourseId(42L)).thenReturn(3L);
        when(taskResultRepository.countCompletedTasksByCourse(1L, 42L)).thenReturn(1L);
        when(taskResultRepository.findAverageScoreByCourse(1L, 42L)).thenReturn(null);

        CourseProgressDto dto = service.getCourseProgress(1L, 42L);

        assertThat(dto.totalLessons()).isEqualTo(0);
        assertThat(dto.completedLessons()).isEqualTo(0);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private TaskResult makeResult(Long studentId, Long taskId, Instant createdAt) {
        TaskResult r = new TaskResult();
        r.setStudentId(studentId);
        r.setTaskId(taskId);
        r.setStatus(ResultStatus.CHECKED);
        // createdAt is set via @CreationTimestamp in production; use reflection in tests
        try {
            var field = TaskResult.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(r, createdAt);
        } catch (Exception e) {
            throw new RuntimeException("Could not set createdAt via reflection", e);
        }
        return r;
    }
}

package com.lms.learning.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lms.learning.config.RequestIdFilter;
import com.lms.learning.config.SecurityConfig;
import com.lms.learning.dto.CourseProgressDto;
import com.lms.learning.dto.StudentProgressDto;
import com.lms.learning.security.JwtAuthenticationFilter;
import com.lms.learning.security.JwtService;
import com.lms.learning.security.JwtUserPrincipal;
import com.lms.learning.security.RoleName;
import com.lms.learning.service.StudentLearningService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = StudentLearningController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RequestIdFilter.class})
class StudentLearningControllerTest {

    private static final long STUDENT_ID = 5L;

    @Autowired
    MockMvc mockMvc;

    @MockBean
    StudentLearningService studentLearningService;

    @MockBean
    JwtService jwtService;

    private static RequestPostProcessor asStudent() {
        JwtUserPrincipal principal = new JwtUserPrincipal(STUDENT_ID, "s@test.com", RoleName.STUDENT);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return authentication(auth);
    }

    private static RequestPostProcessor asTeacher() {
        JwtUserPrincipal principal = new JwtUserPrincipal(99L, "t@test.com", RoleName.TEACHER);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return authentication(auth);
    }

    // ── GET /api/v1/learning/student/progress ─────────────────────────────────

    @Test
    void getProgress_asStudent_returns200WithCorrectFields() throws Exception {
        StudentProgressDto.DailyStatDto day = new StudentProgressDto.DailyStatDto("2026-06-08", 2, 10);
        StudentProgressDto dto = new StudentProgressDto(3, 30, 10, 2, 0, 14, List.of(day));
        when(studentLearningService.getProgress(eq(STUDENT_ID))).thenReturn(dto);

        mockMvc.perform(get("/api/v1/learning/student/progress").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.streakDays").value(3))
                .andExpect(jsonPath("$.dailyGoalMinutes").value(30))
                .andExpect(jsonPath("$.todayMinutesEstimate").value(10))
                .andExpect(jsonPath("$.todayTasksCompleted").value(2))
                .andExpect(jsonPath("$.weekLessonsCompleted").value(0))
                .andExpect(jsonPath("$.weekTasksCompleted").value(14))
                .andExpect(jsonPath("$.last7Days[0].date").value("2026-06-08"))
                .andExpect(jsonPath("$.last7Days[0].tasksCompleted").value(2))
                .andExpect(jsonPath("$.last7Days[0].minutesEstimate").value(10));
    }

    @Test
    void getProgress_asTeacher_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/learning/student/progress").with(asTeacher()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getProgress_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/learning/student/progress"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/v1/learning/student/courses/{courseId}/progress ─────────────

    @Test
    void getCourseProgress_asStudent_returns200WithCorrectFields() throws Exception {
        CourseProgressDto dto = new CourseProgressDto(42L, 10, 7, 0, 0, 70.0, 85.5);
        when(studentLearningService.getCourseProgress(eq(STUDENT_ID), eq(42L))).thenReturn(dto);

        mockMvc.perform(get("/api/v1/learning/student/courses/42/progress").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").value(42))
                .andExpect(jsonPath("$.totalTasks").value(10))
                .andExpect(jsonPath("$.completedTasks").value(7))
                .andExpect(jsonPath("$.progressPercent").value(70.0))
                .andExpect(jsonPath("$.averageScore").value(85.5));
    }

    @Test
    void getCourseProgress_asTeacher_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/learning/student/courses/42/progress").with(asTeacher()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCourseProgress_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/learning/student/courses/42/progress"))
                .andExpect(status().isUnauthorized());
    }
}

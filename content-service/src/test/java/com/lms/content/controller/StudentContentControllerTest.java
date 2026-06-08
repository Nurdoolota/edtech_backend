package com.lms.content.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lms.content.config.RequestIdFilter;
import com.lms.content.config.SecurityConfig;
import com.lms.content.dto.student.StudentCourseResponse;
import com.lms.content.dto.student.StudentCourseTreeResponse;
import com.lms.content.dto.student.StudentTaskAvailabilityResponse;
import com.lms.content.entity.TaskType;
import com.lms.content.security.JwtAuthenticationFilter;
import com.lms.content.security.JwtService;
import com.lms.content.security.JwtUserPrincipal;
import com.lms.content.security.RoleName;
import com.lms.content.service.StudentContentService;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = StudentContentController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RequestIdFilter.class})
class StudentContentControllerTest {

    private static final long STUDENT_ID = 20L;

    @Autowired MockMvc mockMvc;
    @MockBean StudentContentService studentContentService;
    @MockBean JwtService jwtService;

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

    // GET /api/v1/content/student/courses
    @Test
    void listCourses_asStudent_returns200() throws Exception {
        StudentCourseResponse course = new StudentCourseResponse(
                1L, "English A1", "desc", "A1", "OPEN", 5, 2, 40.0, Instant.now());
        when(studentContentService.listCourses(eq(STUDENT_ID))).thenReturn(List.of(course));

        mockMvc.perform(get("/api/v1/content/student/courses").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("0.id").value(1))
                .andExpect(jsonPath("0.title").value("English A1"))
                .andExpect(jsonPath("0.lessonCount").value(5));
    }

    @Test
    void listCourses_asTeacher_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/content/student/courses").with(asTeacher()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listCourses_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/content/student/courses"))
                .andExpect(status().isUnauthorized());
    }

    // GET /api/v1/content/student/courses/{id}
    @Test
    void getCourseTree_asStudent_returns200() throws Exception {
        StudentCourseTreeResponse tree = new StudentCourseTreeResponse(
                1L, "English A1", "desc", "A1", "OPEN", "OPEN", Collections.emptyList());
        when(studentContentService.getCourseTree(eq(1L), eq(STUDENT_ID))).thenReturn(tree);

        mockMvc.perform(get("/api/v1/content/student/courses/1").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("English A1"))
                .andExpect(jsonPath("$.topics").isArray());
    }

    @Test
    void getCourseTree_asTeacher_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/content/student/courses/1").with(asTeacher()))
                .andExpect(status().isForbidden());
    }

    // GET /api/v1/content/student/lessons/{id}/available-tasks
    @Test
    void getAvailableTasks_asStudent_returns200() throws Exception {
        StudentTaskAvailabilityResponse task = new StudentTaskAvailabilityResponse(
                5L, "Task 1", TaskType.FILL_BLANKS, 0, "FREE", false, null, "CHECKED", 85);
        when(studentContentService.getAvailableTasks(eq(3L), eq(STUDENT_ID))).thenReturn(List.of(task));

        mockMvc.perform(get("/api/v1/content/student/lessons/3/available-tasks").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("0.taskId").value(5))
                .andExpect(jsonPath("0.locked").value(false))
                .andExpect(jsonPath("0.lastStatus").value("CHECKED"))
                .andExpect(jsonPath("0.lastScore").value(85));
    }

    @Test
    void getAvailableTasks_asTeacher_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/content/student/lessons/3/available-tasks").with(asTeacher()))
                .andExpect(status().isForbidden());
    }
}

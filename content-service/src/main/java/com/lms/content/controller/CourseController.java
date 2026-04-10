package com.lms.content.controller;

import com.lms.content.dto.PagedResponse;
import com.lms.content.dto.course.CourseResponse;
import com.lms.content.dto.course.CreateCourseRequest;
import com.lms.content.dto.course.UpdateCourseRequest;
import com.lms.content.dto.task.TaskResponse;
import com.lms.content.security.JwtUserPrincipal;
import com.lms.content.service.CourseService;
import com.lms.content.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/content/courses")
@Tag(name = "Courses")
public class CourseController {

    private final CourseService courseService;
    private final TaskService taskService;

    public CourseController(CourseService courseService, TaskService taskService) {
        this.courseService = courseService;
        this.taskService = taskService;
    }

    @GetMapping
    @Operation(summary = "List courses (filtered by role)")
    public PagedResponse<CourseResponse> list(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return courseService.findAll(principal, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get course by id")
    public CourseResponse getById(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        return courseService.findById(id, principal);
    }

    @PostMapping
    @Operation(summary = "Create course (TEACHER, ADMIN)")
    public ResponseEntity<CourseResponse> create(
            @Valid @RequestBody CreateCourseRequest req,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(courseService.create(req, principal));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update course (owner TEACHER or ADMIN)")
    public CourseResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCourseRequest req,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        return courseService.update(id, req, principal);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete course (owner TEACHER or ADMIN)")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        courseService.delete(id, principal);
        return ResponseEntity.noContent().build();
    }

    // --- Topics = Tasks within a course ---

    @GetMapping("/{courseId}/topics")
    @Operation(summary = "List tasks (topics) for a course")
    public PagedResponse<TaskResponse> listTopics(
            @PathVariable Long courseId,
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return taskService.findByCourse(courseId, principal, pageable);
    }
}

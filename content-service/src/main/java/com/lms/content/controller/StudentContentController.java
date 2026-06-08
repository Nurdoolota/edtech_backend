package com.lms.content.controller;

import com.lms.content.dto.block.BlockResponse;
import com.lms.content.dto.lesson.LessonWithContentResponse;
import com.lms.content.dto.student.StudentCourseResponse;
import com.lms.content.dto.student.StudentCourseTreeResponse;
import com.lms.content.dto.student.StudentTaskAvailabilityResponse;
import com.lms.content.dto.task.TaskResponse;
import com.lms.content.security.JwtUserPrincipal;
import com.lms.content.service.StudentContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/content/student")
@Tag(name = "Student Content", description = "Student-facing course and lesson data")
public class StudentContentController {

    private final StudentContentService studentContentService;

    public StudentContentController(StudentContentService studentContentService) {
        this.studentContentService = studentContentService;
    }

    @GetMapping("/courses")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "List courses for the enrolled student")
    public ResponseEntity<List<StudentCourseResponse>> listCourses(
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        return ResponseEntity.ok(studentContentService.listCourses(principal.getUserId()));
    }

    @GetMapping("/courses/{id}")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Get student course tree with locked flags")
    public ResponseEntity<StudentCourseTreeResponse> getCourseTree(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        return ResponseEntity.ok(studentContentService.getCourseTree(id, principal.getUserId()));
    }

    @GetMapping("/lessons/{id}")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Get lesson with content for student")
    public ResponseEntity<LessonWithContentResponse> getLesson(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        return ResponseEntity.ok(studentContentService.getLesson(id, principal.getUserId()));
    }

    @GetMapping("/lessons/{id}/blocks")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Get lesson blocks for student")
    public ResponseEntity<List<BlockResponse>> getLessonBlocks(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        return ResponseEntity.ok(studentContentService.getLessonBlocks(id, principal.getUserId()));
    }

    @GetMapping("/lessons/{id}/tasks")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Get lesson tasks for student")
    public ResponseEntity<List<TaskResponse>> getLessonTasks(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        return ResponseEntity.ok(studentContentService.getLessonTasks(id, principal.getUserId()));
    }

    @GetMapping("/lessons/{id}/available-tasks")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Get available tasks with locked/unlock info for student")
    public ResponseEntity<List<StudentTaskAvailabilityResponse>> getAvailableTasks(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        return ResponseEntity.ok(studentContentService.getAvailableTasks(id, principal.getUserId()));
    }
}

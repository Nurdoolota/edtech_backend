package com.lms.learning.controller;

import com.lms.learning.dto.CourseProgressDto;
import com.lms.learning.dto.StudentProgressDto;
import com.lms.learning.security.JwtUserPrincipal;
import com.lms.learning.service.StudentLearningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/learning/student")
@Tag(name = "Student Learning", description = "Student progress and course stats")
public class StudentLearningController {

    private final StudentLearningService studentLearningService;

    public StudentLearningController(StudentLearningService studentLearningService) {
        this.studentLearningService = studentLearningService;
    }

    @GetMapping("/progress")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Get student's overall progress: streak, daily/weekly stats, last 7 days")
    public ResponseEntity<StudentProgressDto> getProgress(
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        return ResponseEntity.ok(studentLearningService.getProgress(principal.getUserId()));
    }

    @GetMapping("/courses/{courseId}/progress")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Get student's progress for a specific course")
    public ResponseEntity<CourseProgressDto> getCourseProgress(
            @PathVariable Long courseId,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        return ResponseEntity.ok(studentLearningService.getCourseProgress(principal.getUserId(), courseId));
    }
}

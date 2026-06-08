package com.lms.content.controller;

import com.lms.content.dto.course.AddCoAuthorRequest;
import com.lms.content.dto.course.CoAuthorResponse;
import com.lms.content.service.CourseTeacherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/content/courses/{courseId}/authors")
@Tag(name = "Course co-authors")
public class CourseTeacherController {

    private final CourseTeacherService courseTeacherService;

    public CourseTeacherController(CourseTeacherService courseTeacherService) {
        this.courseTeacherService = courseTeacherService;
    }

    @GetMapping
    @Operation(summary = "List co-authors of a course")
    public ResponseEntity<List<CoAuthorResponse>> list(
            @PathVariable Long courseId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(courseTeacherService.list(courseId, userId, role));
    }

    @PostMapping
    @Operation(summary = "Add a co-author to a course")
    public ResponseEntity<CoAuthorResponse> add(
            @PathVariable Long courseId,
            @Valid @RequestBody AddCoAuthorRequest req,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(courseTeacherService.add(courseId, req, userId, role));
    }

    @DeleteMapping("/{targetUserId}")
    @Operation(summary = "Remove a co-author from a course")
    public ResponseEntity<Void> remove(
            @PathVariable Long courseId,
            @PathVariable Long targetUserId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        courseTeacherService.remove(courseId, targetUserId, userId, role);
        return ResponseEntity.noContent().build();
    }
}

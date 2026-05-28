package com.lms.content.controller;

import com.lms.content.dto.lesson.GrantAccessRequest;
import com.lms.content.dto.lesson.LessonAccessEntryResponse;
import com.lms.content.dto.lesson.LessonAccessResponse;
import com.lms.content.dto.lesson.LessonRequest;
import com.lms.content.dto.lesson.LessonResponse;
import com.lms.content.dto.lesson.LessonWithContentResponse;
import com.lms.content.dto.lesson.PublishRequest;
import com.lms.content.dto.lesson.TaskOrderResponse;
import com.lms.content.dto.lesson.TaskReorderRequest;
import com.lms.content.service.LessonService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/content")
public class LessonController {

    private final LessonService lessonService;

    public LessonController(LessonService lessonService) {
        this.lessonService = lessonService;
    }

    @GetMapping("/courses/{courseId}/lessons")
    public ResponseEntity<List<LessonResponse>> listByCourse(
            @PathVariable Long courseId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(lessonService.listByCourse(courseId, userId, role));
    }

    @PostMapping("/courses/{courseId}/lessons")
    public ResponseEntity<LessonResponse> createInCourse(
            @PathVariable Long courseId,
            @Valid @RequestBody LessonRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(lessonService.create(courseId, request, userId, role));
    }

    @PostMapping("/topics/{topicId}/lessons")
    public ResponseEntity<LessonResponse> createInTopic(
            @PathVariable Long topicId,
            @Valid @RequestBody LessonRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(lessonService.createInTopic(topicId, request, userId, role));
    }

    @GetMapping("/lessons/{id}")
    public ResponseEntity<LessonWithContentResponse> getById(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(lessonService.getById(id, userId, role));
    }

    @PatchMapping("/lessons/{id}")
    public ResponseEntity<LessonResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody LessonRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(lessonService.update(id, request, userId, role));
    }

    @DeleteMapping("/lessons/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        lessonService.delete(id, userId, role);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/lessons/{id}/publish")
    public ResponseEntity<LessonResponse> publish(
            @PathVariable Long id,
            @RequestBody PublishRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(lessonService.publish(id, request.confirmed(), userId, role));
    }

    @PatchMapping("/lessons/{id}/tasks/reorder")
    public ResponseEntity<List<TaskOrderResponse>> reorderTasks(
            @PathVariable Long id,
            @Valid @RequestBody TaskReorderRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(lessonService.reorderTasks(id, request.order(), userId, role));
    }

    @PostMapping("/lessons/{id}/access")
    public ResponseEntity<LessonAccessResponse> grantAccess(
            @PathVariable Long id,
            @Valid @RequestBody GrantAccessRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(lessonService.grantAccess(id, request.studentId(), userId, role));
    }

    @DeleteMapping("/lessons/{id}/access/{studentId}")
    public ResponseEntity<Void> revokeAccess(
            @PathVariable Long id,
            @PathVariable Long studentId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        lessonService.revokeAccess(id, studentId, userId, role);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/lessons/{id}/access")
    public ResponseEntity<List<LessonAccessEntryResponse>> listAccess(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(lessonService.listAccess(id, userId, role));
    }
}

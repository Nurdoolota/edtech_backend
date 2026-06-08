package com.lms.content.controller;

import com.lms.content.dto.ReorderRequest;
import com.lms.content.dto.lesson.LessonResponse;
import com.lms.content.dto.topic.TopicRequest;
import com.lms.content.dto.topic.TopicResponse;
import com.lms.content.dto.topic.TopicWithLessonsResponse;
import com.lms.content.service.CourseItemsReorderService;
import com.lms.content.service.TopicService;
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
public class TopicController {

    private final TopicService topicService;
    private final CourseItemsReorderService courseItemsReorderService;

    public TopicController(TopicService topicService,
            CourseItemsReorderService courseItemsReorderService) {
        this.topicService = topicService;
        this.courseItemsReorderService = courseItemsReorderService;
    }

    @GetMapping("/courses/{courseId}/topics")
    public ResponseEntity<List<TopicResponse>> listByCourse(
            @PathVariable Long courseId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(topicService.listByCourse(courseId, userId, role));
    }

    @PostMapping("/courses/{courseId}/topics")
    public ResponseEntity<TopicResponse> create(
            @PathVariable Long courseId,
            @Valid @RequestBody TopicRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(topicService.create(courseId, request, userId, role));
    }

    @GetMapping("/topics/{topicId}")
    public ResponseEntity<TopicWithLessonsResponse> getById(
            @PathVariable Long topicId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(topicService.getById(topicId, userId, role));
    }

    @GetMapping("/topics/{topicId}/full")
    public ResponseEntity<TopicWithLessonsResponse> getFull(
            @PathVariable Long topicId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(topicService.getFull(topicId, userId, role));
    }

    @PatchMapping("/topics/{topicId}")
    public ResponseEntity<TopicResponse> update(
            @PathVariable Long topicId,
            @Valid @RequestBody TopicRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(topicService.update(topicId, request, userId, role));
    }

    @DeleteMapping("/topics/{topicId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long topicId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        topicService.delete(topicId, userId, role);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/courses/{courseId}/topics/reorder")
    public ResponseEntity<List<TopicResponse>> reorder(
            @PathVariable Long courseId,
            @Valid @RequestBody ReorderRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(topicService.reorder(courseId, request.order(), userId, role));
    }

    @PatchMapping("/topics/{topicId}/lessons/reorder")
    public ResponseEntity<List<LessonResponse>> reorderLessons(
            @PathVariable Long topicId,
            @Valid @RequestBody ReorderRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(courseItemsReorderService.reorderTopicLessons(topicId, request.order(), userId, role));
    }
}

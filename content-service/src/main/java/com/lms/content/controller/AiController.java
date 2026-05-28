package com.lms.content.controller;

import com.lms.content.dto.ai.GenerateLessonRequest;
import com.lms.content.dto.ai.GenerateTaskRequest;
import com.lms.content.dto.ai.GenerateTopicRequest;
import com.lms.content.dto.ai.GenerateTopicResponse;
import com.lms.content.dto.ai.RegenerateLessonRequest;
import com.lms.content.dto.lesson.LessonResponse;
import com.lms.content.dto.task.TaskResponse;
import com.lms.content.service.AiGenerationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/content")
public class AiController {

    private final AiGenerationService aiGenerationService;

    public AiController(AiGenerationService aiGenerationService) {
        this.aiGenerationService = aiGenerationService;
    }

    @PostMapping("/ai/generate-lesson")
    public ResponseEntity<LessonResponse> generateLesson(
            @Valid @RequestBody GenerateLessonRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(aiGenerationService.generateLesson(request, userId, role));
    }

    @PostMapping("/ai/generate-task")
    public ResponseEntity<TaskResponse> generateTask(
            @Valid @RequestBody GenerateTaskRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(aiGenerationService.generateTask(request, userId, role));
    }

    @PostMapping("/ai/generate-topic")
    public ResponseEntity<GenerateTopicResponse> generateTopic(
            @Valid @RequestBody GenerateTopicRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(aiGenerationService.generateTopic(request, userId, role));
    }

    @PostMapping("/lessons/{id}/regenerate")
    public ResponseEntity<LessonResponse> regenerateLesson(
            @PathVariable Long id,
            @RequestBody RegenerateLessonRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(aiGenerationService.regenerateLesson(id, request, userId, role));
    }
}

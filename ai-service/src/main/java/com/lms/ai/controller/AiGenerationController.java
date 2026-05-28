package com.lms.ai.controller;

import com.lms.ai.dto.gen.AiGenerateLessonRequest;
import com.lms.ai.dto.gen.AiGenerateTaskRequest;
import com.lms.ai.dto.gen.AiGenerateTopicRequest;
import com.lms.ai.dto.gen.AiLessonJson;
import com.lms.ai.dto.gen.AiRegenerateLessonRequest;
import com.lms.ai.dto.gen.AiTaskJson;
import com.lms.ai.dto.gen.AiTopicJson;
import com.lms.ai.service.AiGenerationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/ai")
public class AiGenerationController {

    private final AiGenerationService generationService;

    public AiGenerationController(AiGenerationService generationService) {
        this.generationService = generationService;
    }

    @PostMapping("/generate-lesson")
    public AiLessonJson generateLesson(
            @Valid @RequestBody AiGenerateLessonRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return generationService.generateLesson(request, userId);
    }

    @PostMapping("/generate-task")
    public AiTaskJson generateTask(
            @Valid @RequestBody AiGenerateTaskRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return generationService.generateTask(request, userId);
    }

    @PostMapping("/generate-topic")
    public AiTopicJson generateTopic(
            @Valid @RequestBody AiGenerateTopicRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return generationService.generateTopic(request, userId);
    }

    @PostMapping("/regenerate-lesson")
    public AiLessonJson regenerateLesson(
            @Valid @RequestBody AiRegenerateLessonRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return generationService.regenerateLesson(request, userId);
    }
}

package com.lms.content.controller;

import com.lms.content.dto.internal.LearningAccessResponse;
import com.lms.content.service.LearningAccessService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal API for learning-service: verifies group/course membership before submit.
 * Not exposed through the public gateway; called service-to-service only.
 */
@RestController
@RequestMapping("/internal/learning")
@Hidden
public class InternalLearningAccessController {

    private final LearningAccessService learningAccessService;

    public InternalLearningAccessController(LearningAccessService learningAccessService) {
        this.learningAccessService = learningAccessService;
    }

    @GetMapping("/access")
    public LearningAccessResponse checkAccess(
            @RequestParam("studentId") Long studentId, @RequestParam("taskId") Long taskId) {
        boolean hasAccess = learningAccessService.hasStudentAccessToTask(studentId, taskId);
        return new LearningAccessResponse(hasAccess);
    }
}

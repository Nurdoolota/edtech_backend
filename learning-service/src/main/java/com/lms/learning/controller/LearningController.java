package com.lms.learning.controller;

import com.lms.learning.dto.PagedResponse;
import com.lms.learning.dto.SubmitAnswerRequest;
import com.lms.learning.dto.TaskResultResponse;
import com.lms.learning.dto.ValidateResultRequest;
import com.lms.learning.security.JwtUserPrincipal;
import com.lms.learning.service.LearningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/learning")
@Tag(name = "Learning", description = "Answer submission, evaluation, results")
public class LearningController {

    private final LearningService learningService;

    public LearningController(LearningService learningService) {
        this.learningService = learningService;
    }

    @PostMapping("/tasks/{taskId}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Submit an answer for a task")
    public ResponseEntity<TaskResultResponse> submit(
            @PathVariable Long taskId,
            @Valid @RequestBody SubmitAnswerRequest request,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        TaskResultResponse response = learningService.submitAnswer(
                taskId, principal.getUserId(), request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-results")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Get the authenticated student's result history")
    public ResponseEntity<PagedResponse<TaskResultResponse>> myResults(
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(
                learningService.getMyResults(principal.getUserId(), courseId, pageable));
    }

    @GetMapping("/tasks/{taskId}/results")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Get all results for a task (teacher view, paginated)")
    public ResponseEntity<PagedResponse<TaskResultResponse>> taskResults(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(learningService.getTaskResults(taskId, pageable));
    }

    @PatchMapping("/results/{resultId}/validate")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Validate / override a result score (teacher)")
    public ResponseEntity<TaskResultResponse> validate(
            @PathVariable Long resultId,
            @Valid @RequestBody ValidateResultRequest request) {
        return ResponseEntity.ok(learningService.validateResult(resultId, request));
    }
}

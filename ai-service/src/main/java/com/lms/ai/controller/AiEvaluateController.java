package com.lms.ai.controller;

import com.lms.ai.dto.AiEvaluateRequest;
import com.lms.ai.dto.AiEvaluateResponse;
import com.lms.ai.service.AiEvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/ai")
@Tag(name = "AI Evaluation", description = "Internal endpoint for LLM-based task evaluation")
public class AiEvaluateController {

    private final AiEvaluationService evaluationService;

    public AiEvaluateController(AiEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping("/evaluate")
    @Operation(summary = "Evaluate a student answer using LLM")
    public ResponseEntity<AiEvaluateResponse> evaluate(@Valid @RequestBody AiEvaluateRequest request) {
        AiEvaluateResponse response = evaluationService.evaluate(request);
        return ResponseEntity.ok(response);
    }
}

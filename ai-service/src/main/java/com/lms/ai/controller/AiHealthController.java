package com.lms.ai.controller;

import com.lms.ai.dto.HealthResponse;
import com.lms.ai.service.AiHealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/ai")
@Tag(name = "AI Health", description = "Internal LLM connectivity probe")
public class AiHealthController {

    private final AiHealthService healthService;

    public AiHealthController(AiHealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/health")
    @Operation(summary = "Probe LLM connectivity and return status, model name, and latency")
    public ResponseEntity<HealthResponse> health() {
        HealthResponse response = healthService.probe();
        // Always 200 — the 'ai' field signals UP/DOWN
        return ResponseEntity.ok(response);
    }
}

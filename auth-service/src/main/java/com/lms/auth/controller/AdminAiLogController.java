package com.lms.auth.controller;

import com.lms.auth.client.AiLogClient;
import com.lms.auth.dto.PagedAiLogResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin AI log")
public class AdminAiLogController {

    private final AiLogClient aiLogClient;

    public AdminAiLogController(AiLogClient aiLogClient) {
        this.aiLogClient = aiLogClient;
    }

    @GetMapping("/ai-log")
    @Operation(summary = "Get AI call log (proxied from ai-service)")
    public ResponseEntity<PagedAiLogResponse> getAiLog(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(aiLogClient.getAiLog(page, size, userId, status));
    }
}

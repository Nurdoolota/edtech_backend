package com.lms.learning.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

/**
 * Accepts any JSON value: string (JSON text), array, or object. Stored as jsonb in task_results.
 */
public record SubmitAnswerRequest(
        @NotNull(message = "answer_content is required")
        JsonNode answerContent) {}

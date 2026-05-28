package com.lms.learning.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record SubmitAnswerRequest(
        @NotNull(message = "answer_content is required")
        JsonNode answerContent,
        String mediaId) {

    public SubmitAnswerRequest(JsonNode answerContent) {
        this(answerContent, null);
    }
}

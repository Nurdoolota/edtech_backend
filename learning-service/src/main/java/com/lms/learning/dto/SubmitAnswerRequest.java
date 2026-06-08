package com.lms.learning.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record SubmitAnswerRequest(
        JsonNode answerContent,
        String mediaId,
        String transcript) {

    public SubmitAnswerRequest(JsonNode answerContent) {
        this(answerContent, null, null);
    }

    public SubmitAnswerRequest(JsonNode answerContent, String mediaId) {
        this(answerContent, mediaId, null);
    }
}

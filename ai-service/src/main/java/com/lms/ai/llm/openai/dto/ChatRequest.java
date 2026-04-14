package com.lms.ai.llm.openai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ChatRequest(
        String model,
        List<Message> messages,
        double temperature,
        @JsonProperty("max_tokens") int maxTokens) {

    public record Message(String role, String content) {}
}

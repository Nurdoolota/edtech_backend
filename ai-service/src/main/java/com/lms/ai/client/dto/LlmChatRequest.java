package com.lms.ai.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * OpenAI-compatible chat completions request body.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LlmChatRequest(
        String model,
        List<Message> messages,
        @JsonProperty("max_tokens") int maxTokens,
        double temperature,
        @JsonProperty("response_format") ResponseFormat responseFormat) {

    public record Message(String role, String content) {}

    public record ResponseFormat(String type) {}
}

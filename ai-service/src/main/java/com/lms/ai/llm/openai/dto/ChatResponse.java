package com.lms.ai.llm.openai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatResponse(List<Choice> choices, Usage usage) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(Message message, @com.fasterxml.jackson.annotation.JsonProperty("finish_reason") String finishReason) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String role, String content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @com.fasterxml.jackson.annotation.JsonProperty("prompt_tokens") int promptTokens,
            @com.fasterxml.jackson.annotation.JsonProperty("completion_tokens") int completionTokens,
            @com.fasterxml.jackson.annotation.JsonProperty("total_tokens") int totalTokens) {}
}

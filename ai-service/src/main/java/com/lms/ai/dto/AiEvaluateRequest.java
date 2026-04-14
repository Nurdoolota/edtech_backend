package com.lms.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record AiEvaluateRequest(
        @NotBlank
        @JsonProperty("task_type")
        String taskType,

        @NotBlank
        @JsonProperty("prompt_template_code")
        String promptTemplateCode,

        @NotNull
        @JsonProperty("task_content")
        Map<String, Object> taskContent,

        @NotBlank
        @JsonProperty("student_answer")
        String studentAnswer,

        @Valid
        AiOptions options) {

    public record AiOptions(
            double temperature,
            @JsonProperty("max_tokens") int maxTokens) {
    }
}

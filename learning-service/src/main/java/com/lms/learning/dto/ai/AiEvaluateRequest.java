package com.lms.learning.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record AiEvaluateRequest(
        @JsonProperty("task_type")
        String taskType,
        @JsonProperty("prompt_template_code")
        String promptTemplateCode,
        @JsonProperty("task_content")
        Map<String, Object> taskContent,
        @JsonProperty("student_answer")
        String studentAnswer,
        AiOptions options) {

    public record AiOptions(double temperature, @JsonProperty("max_tokens") int maxTokens) {}
}

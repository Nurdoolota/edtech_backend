package com.lms.ai.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
        @NotBlank String apiBaseUrl,
        @NotBlank String model,
        @NotBlank String apiKey,
        @Positive int timeoutSeconds) {
}

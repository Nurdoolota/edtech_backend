package com.lms.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HealthResponse(
        String ai,
        String model,
        @JsonProperty("latencyMs") int latencyMs) {
}

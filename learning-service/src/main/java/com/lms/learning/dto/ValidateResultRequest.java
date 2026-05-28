package com.lms.learning.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ValidateResultRequest(
        @NotNull(message = "score is required")
        BigDecimal score,
        String comment) {}

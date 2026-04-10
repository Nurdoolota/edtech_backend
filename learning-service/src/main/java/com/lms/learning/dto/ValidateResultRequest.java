package com.lms.learning.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ValidateResultRequest(
        @NotNull(message = "score is required")
        @DecimalMin(value = "0.0", message = "score must be >= 0")
        @DecimalMax(value = "100.0", message = "score must be <= 100")
        BigDecimal score,
        String comment) {}

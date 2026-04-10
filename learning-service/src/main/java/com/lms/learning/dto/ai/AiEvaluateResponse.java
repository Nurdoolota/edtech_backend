package com.lms.learning.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiEvaluateResponse(
        BigDecimal score,
        String feedback,
        String verdict) {}

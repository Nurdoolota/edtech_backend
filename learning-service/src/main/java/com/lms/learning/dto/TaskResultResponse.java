package com.lms.learning.dto;

import com.lms.learning.entity.ResultStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record TaskResultResponse(
        Long id,
        Long taskId,
        Long studentId,
        String answerContent,
        String aiFeedback,
        BigDecimal score,
        ResultStatus status,
        Instant createdAt,
        Instant updatedAt) {}

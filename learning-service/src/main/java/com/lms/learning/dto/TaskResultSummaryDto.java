package com.lms.learning.dto;

import com.lms.learning.entity.ResultStatus;

public record TaskResultSummaryDto(
        Long taskId,
        ResultStatus status,
        Integer aiScore,
        Integer teacherScore) {}

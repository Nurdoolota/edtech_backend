package com.lms.learning.dto;

import java.time.Instant;

public record StudentStatsDto(
        long tasksSubmitted,
        Double averageScore,
        Instant lastActivity) {}

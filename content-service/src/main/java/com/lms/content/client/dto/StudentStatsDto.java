package com.lms.content.client.dto;

import java.time.Instant;

public record StudentStatsDto(Integer tasksSubmitted, Double averageScore, Instant lastActivity) {}

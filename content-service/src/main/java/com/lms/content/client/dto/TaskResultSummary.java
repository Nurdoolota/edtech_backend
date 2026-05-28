package com.lms.content.client.dto;

public record TaskResultSummary(Long taskId, String status, Integer aiScore, Integer teacherScore) {}

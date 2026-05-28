package com.lms.content.dto.lesson;

public record TaskAvailabilityResult(Long taskId, boolean available, String reason) {}

package com.lms.ai.dto;

public record ApiError(String code, String message, String requestId) {
}

package com.lms.learning.dto;

public record ApiError(String code, String message, String requestId) {}

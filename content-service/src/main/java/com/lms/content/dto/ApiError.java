package com.lms.content.dto;

public record ApiError(String code, String message, String requestId) {}

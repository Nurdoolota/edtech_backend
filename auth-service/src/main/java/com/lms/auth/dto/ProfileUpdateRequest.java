package com.lms.auth.dto;

import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Size(max = 500) String bio,
        String locale,
        String timezone,
        Boolean emailPrivate,
        String notifications) {}

package com.lms.auth.dto;

import com.lms.auth.entity.RoleName;
import java.time.Instant;

public record UserResponse(
        long id,
        String email,
        String firstName,
        String lastName,
        String displayName,
        RoleName role,
        boolean active,
        String avatarUrl,
        String bio,
        String locale,
        String timezone,
        boolean emailPrivate,
        String notifications,
        Instant lastLoginAt,
        Instant updatedAt,
        Instant createdAt) {}

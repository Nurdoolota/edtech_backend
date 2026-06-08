package com.lms.auth.dto;

import com.lms.auth.entity.RoleName;
import java.time.Instant;

public record PublicProfileResponse(
        long id,
        String firstName,
        String lastName,
        String displayName,
        RoleName role,
        String avatarUrl,
        String bio,
        String email,
        Instant createdAt
) {}

package com.lms.content.dto.course;

import java.time.Instant;

public record CoAuthorResponse(
        Long userId,
        String role,
        Instant addedAt
) {}

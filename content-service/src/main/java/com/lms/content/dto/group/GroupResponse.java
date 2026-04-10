package com.lms.content.dto.group;

import java.time.Instant;

public record GroupResponse(
        Long id,
        String name,
        Long teacherId,
        Instant createdAt) {}

package com.lms.content.dto.course;

import java.time.Instant;

public record CourseResponse(
        Long id,
        String title,
        String description,
        Long authorId,
        Instant createdAt,
        Instant updatedAt) {}

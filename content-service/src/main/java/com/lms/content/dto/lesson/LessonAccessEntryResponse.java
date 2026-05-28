package com.lms.content.dto.lesson;

import java.time.Instant;

public record LessonAccessEntryResponse(Long studentId, Instant grantedAt) {}

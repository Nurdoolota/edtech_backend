package com.lms.content.dto.lesson;

import java.time.Instant;

public record LessonAccessResponse(Long lessonId, Long studentId, Instant grantedAt) {}

package com.lms.content.dto.lesson;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LessonRequest(
        @NotBlank @Size(max = 500) String title,
        Long topicId,
        String publishMode,
        String unlockMode,
        Boolean visible
) {}

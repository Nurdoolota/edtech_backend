package com.lms.content.dto.topic;

import com.lms.content.dto.lesson.LessonSummaryResponse;
import java.time.Instant;
import java.util.List;

public record TopicWithLessonsResponse(
        Long id,
        Long courseId,
        String title,
        String description,
        int orderIndex,
        Integer globalOrder,
        Instant createdAt,
        List<LessonSummaryResponse> lessons
) {}

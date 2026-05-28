package com.lms.content.dto.lesson;

import com.lms.content.dto.block.BlockResponse;
import java.time.Instant;
import java.util.List;

public record LessonWithContentResponse(
        Long id,
        Long courseId,
        Long topicId,
        int orderIndex,
        Integer globalOrder,
        String title,
        String status,
        String publishMode,
        String unlockMode,
        boolean visible,
        int blocksCount,
        int tasksCount,
        Instant createdAt,
        Instant publishedAt,
        List<BlockResponse> blocks,
        List<Object> tasks
) {}

package com.lms.content.dto.student;

import java.time.Instant;
import java.util.List;

/**
 * Student-facing course tree: topics -> lessons with locked flags.
 */
public record StudentCourseTreeResponse(
        Long id,
        String title,
        String description,
        String level,
        String unlockMode,
        String accessStatus,
        List<TopicNode> topics) {

    public record TopicNode(
            Long id,
            String title,
            String description,
            int orderIndex,
            List<LessonNode> lessons) {}

    public record LessonNode(
            Long id,
            String title,
            String status,
            int orderIndex,
            String unlockMode,
            boolean locked,
            int blocksCount,
            int tasksCount) {}
}

package com.lms.content.dto.course;

import java.util.List;

public record CourseTreeResponse(
        Long id,
        String title,
        String description,
        String level,
        String publishMode,
        String accessStatus,
        List<TopicTreeNode> topics) {

    public record TopicTreeNode(
            Long id,
            String title,
            String description,
            int orderIndex,
            List<LessonSummaryNode> lessons) {}

    public record LessonSummaryNode(
            Long id,
            String title,
            String status,
            int orderIndex,
            String unlockMode,
            boolean visible,
            int blocksCount,
            int tasksCount) {}
}

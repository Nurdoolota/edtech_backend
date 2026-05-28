package com.lms.content.service;

import com.lms.content.dto.course.CourseTreeResponse;
import com.lms.content.dto.course.CourseTreeResponse.LessonSummaryNode;
import com.lms.content.dto.course.CourseTreeResponse.TopicTreeNode;
import com.lms.content.entity.Course;
import com.lms.content.entity.Topic;
import com.lms.content.repository.LessonRepository;
import com.lms.content.repository.TopicRepository;
import com.lms.content.security.JwtUserPrincipal;
import com.lms.content.util.CourseAccessChecker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CourseTreeService {

    private final TopicRepository topicRepository;
    private final LessonRepository lessonRepository;
    private final CourseAccessChecker courseAccessChecker;

    public CourseTreeService(TopicRepository topicRepository,
            LessonRepository lessonRepository,
            CourseAccessChecker courseAccessChecker) {
        this.topicRepository = topicRepository;
        this.lessonRepository = lessonRepository;
        this.courseAccessChecker = courseAccessChecker;
    }

    public CourseTreeResponse getTree(Long courseId, JwtUserPrincipal principal) {
        Course course = courseAccessChecker.loadCourseWithAccess(
                courseId, principal.getUserId(), principal.getRole().name());

        List<Topic> topics = topicRepository.findByCourseIdOrderByOrderIndex(courseId);

        List<Object[]> lessonRows = lessonRepository.findLessonsWithCountsByCourseId(courseId);

        Map<Long, List<LessonSummaryNode>> byTopic = new LinkedHashMap<>();
        for (Object[] row : lessonRows) {
            Long topicId = row[1] != null ? ((Number) row[1]).longValue() : null;
            if (topicId == null) {
                // Lessons without a topic are omitted from the tree (teacher UI always assigns topics)
                continue;
            }
            byTopic.computeIfAbsent(topicId, k -> new ArrayList<>()).add(toLessonNode(row));
        }

        List<TopicTreeNode> topicNodes = topics.stream()
                .map(t -> new TopicTreeNode(
                        t.getId(), t.getTitle(), t.getDescription(), t.getOrderIndex(),
                        byTopic.getOrDefault(t.getId(), Collections.emptyList())))
                .collect(Collectors.toList());

        return new CourseTreeResponse(
                course.getId(), course.getTitle(), course.getDescription(),
                course.getLevel(), course.getPublishMode(), course.getAccessStatus(),
                topicNodes);
    }

    private LessonSummaryNode toLessonNode(Object[] row) {
        Long lessonId = ((Number) row[0]).longValue();
        String title = (String) row[2];
        String status = (String) row[3];
        int orderIndex = ((Number) row[4]).intValue();
        String unlockMode = (String) row[5];
        boolean visible = (Boolean) row[6];
        int blocksCount = ((Number) row[7]).intValue();
        int tasksCount = ((Number) row[8]).intValue();
        return new LessonSummaryNode(lessonId, title, status, orderIndex, unlockMode, visible,
                blocksCount, tasksCount);
    }
}

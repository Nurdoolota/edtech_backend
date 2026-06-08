package com.lms.content.service;

import com.lms.content.dto.course.CourseItemReorderRequest;
import com.lms.content.dto.lesson.LessonResponse;
import com.lms.content.entity.Lesson;
import com.lms.content.entity.Topic;
import com.lms.content.exception.ApiBusinessException;
import com.lms.content.repository.LessonBlockRepository;
import com.lms.content.repository.LessonRepository;
import com.lms.content.repository.TaskRepository;
import com.lms.content.repository.TopicRepository;
import com.lms.content.util.CourseAccessChecker;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseItemsReorderService {

    private final LessonRepository lessonRepository;
    private final TopicRepository topicRepository;
    private final LessonBlockRepository lessonBlockRepository;
    private final TaskRepository taskRepository;
    private final CourseAccessChecker courseAccessChecker;

    public CourseItemsReorderService(LessonRepository lessonRepository,
            TopicRepository topicRepository,
            LessonBlockRepository lessonBlockRepository,
            TaskRepository taskRepository,
            CourseAccessChecker courseAccessChecker) {
        this.lessonRepository = lessonRepository;
        this.topicRepository = topicRepository;
        this.lessonBlockRepository = lessonBlockRepository;
        this.taskRepository = taskRepository;
        this.courseAccessChecker = courseAccessChecker;
    }

    /** Sets global_order on topics and standalone lessons in the provided mixed order. */
    @Transactional
    public void reorderItems(Long courseId, CourseItemReorderRequest req,
            Long userId, String role) {
        courseAccessChecker.checkAccess(courseId, userId, role);
        List<CourseItemReorderRequest.CourseItemEntry> items = req.items();
        for (int i = 0; i < items.size(); i++) {
            CourseItemReorderRequest.CourseItemEntry entry = items.get(i);
            if ("TOPIC".equalsIgnoreCase(entry.type())) {
                Topic topic = topicRepository.findById(entry.id())
                        .orElseThrow(() -> ApiBusinessException.notFound("Topic", entry.id()));
                if (!topic.getCourseId().equals(courseId)) {
                    throw ApiBusinessException.badRequest(
                            "Topic " + entry.id() + " does not belong to course " + courseId);
                }
                topic.setGlobalOrder(i);
                topicRepository.save(topic);
            } else if ("LESSON".equalsIgnoreCase(entry.type())) {
                Lesson lesson = lessonRepository.findById(entry.id())
                        .orElseThrow(() -> ApiBusinessException.notFound("Lesson", entry.id()));
                if (!lesson.getCourseId().equals(courseId)) {
                    throw ApiBusinessException.badRequest(
                            "Lesson " + entry.id() + " does not belong to course " + courseId);
                }
                lesson.setGlobalOrder(i);
                lessonRepository.save(lesson);
            } else {
                throw ApiBusinessException.badRequest("Unknown item type: " + entry.type());
            }
        }
    }

    /** Reorders standalone (no-topic) lessons within a course by orderIndex. */
    @Transactional
    public List<LessonResponse> reorderCourseLessons(Long courseId, List<Long> order,
            Long userId, String role) {
        courseAccessChecker.checkAccess(courseId, userId, role);
        List<Lesson> standalone = lessonRepository.findByCourseIdOrderByOrderIndex(courseId)
                .stream().filter(l -> l.getTopicId() == null).collect(Collectors.toList());
        if (order.size() != standalone.size()
                || !new HashSet<>(order).equals(
                        standalone.stream().map(Lesson::getId).collect(Collectors.toSet()))) {
            throw ApiBusinessException.badRequest(
                    "Order list does not match standalone lessons in course");
        }
        for (int i = 0; i < order.size(); i++) {
            final int newIndex = i;
            final Long lessonId = order.get(i);
            standalone.stream()
                    .filter(l -> l.getId().equals(lessonId))
                    .findFirst()
                    .ifPresent(l -> l.setOrderIndex(newIndex));
        }
        return lessonRepository.saveAll(standalone).stream()
                .sorted((a, b) -> Integer.compare(a.getOrderIndex(), b.getOrderIndex()))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /** Reorders lessons within a topic by orderIndex. */
    @Transactional
    public List<LessonResponse> reorderTopicLessons(Long topicId, List<Long> order,
            Long userId, String role) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> ApiBusinessException.notFound("Topic", topicId));
        courseAccessChecker.checkAccess(topic.getCourseId(), userId, role);
        List<Lesson> topicLessons = lessonRepository.findByTopicIdOrderByOrderIndex(topicId);
        if (order.size() != topicLessons.size()
                || !new HashSet<>(order).equals(
                        topicLessons.stream().map(Lesson::getId).collect(Collectors.toSet()))) {
            throw ApiBusinessException.badRequest(
                    "Order list does not match lessons in topic");
        }
        for (int i = 0; i < order.size(); i++) {
            final int newIndex = i;
            final Long lessonId = order.get(i);
            topicLessons.stream()
                    .filter(l -> l.getId().equals(lessonId))
                    .findFirst()
                    .ifPresent(l -> l.setOrderIndex(newIndex));
        }
        return lessonRepository.saveAll(topicLessons).stream()
                .sorted((a, b) -> Integer.compare(a.getOrderIndex(), b.getOrderIndex()))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private LessonResponse toResponse(Lesson l) {
        int blocksCount = (int) lessonBlockRepository.countByLessonId(l.getId());
        int tasksCount = taskRepository.findByLessonId(l.getId()).size();
        return new LessonResponse(
                l.getId(), l.getCourseId(), l.getTopicId(),
                l.getOrderIndex(), l.getGlobalOrder(),
                l.getTitle(), l.getStatus(),
                l.getPublishMode(), l.getUnlockMode(),
                l.isVisible(), blocksCount, tasksCount,
                l.getCreatedAt(), l.getPublishedAt());
    }
}

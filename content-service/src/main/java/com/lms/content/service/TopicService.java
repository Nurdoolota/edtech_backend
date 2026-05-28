package com.lms.content.service;

import com.lms.content.dto.lesson.LessonRequest;
import com.lms.content.dto.lesson.LessonResponse;
import com.lms.content.dto.lesson.LessonSummaryResponse;
import com.lms.content.dto.topic.TopicRequest;
import com.lms.content.dto.topic.TopicResponse;
import com.lms.content.dto.topic.TopicWithLessonsResponse;
import com.lms.content.entity.Topic;
import com.lms.content.exception.ApiBusinessException;
import com.lms.content.repository.TopicRepository;
import com.lms.content.util.CourseAccessChecker;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TopicService {

    private final TopicRepository topicRepository;
    private final CourseAccessChecker courseAccessChecker;
    private final LessonService lessonService;

    public TopicService(TopicRepository topicRepository,
            CourseAccessChecker courseAccessChecker,
            @Lazy LessonService lessonService) {
        this.topicRepository = topicRepository;
        this.courseAccessChecker = courseAccessChecker;
        this.lessonService = lessonService;
    }

    public List<TopicResponse> listByCourse(Long courseId, Long userId, String role) {
        courseAccessChecker.checkAccess(courseId, userId, role);
        return topicRepository.findByCourseIdOrderByOrderIndex(courseId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public TopicResponse create(Long courseId, TopicRequest request, Long userId, String role) {
        courseAccessChecker.checkAccess(courseId, userId, role);
        Topic topic = new Topic();
        topic.setCourseId(courseId);
        topic.setTitle(request.title());
        topic.setDescription(request.description());
        if (request.orderIndex() != null) {
            topic.setOrderIndex(request.orderIndex());
        } else {
            int nextIndex = topicRepository.findMaxOrderIndexByCourseId(courseId)
                    .map(max -> max + 1)
                    .orElse(0);
            topic.setOrderIndex(nextIndex);
        }
        return toResponse(topicRepository.save(topic));
    }

    public TopicWithLessonsResponse getById(Long topicId, Long userId, String role) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> ApiBusinessException.notFound("Topic", topicId));
        courseAccessChecker.checkAccess(topic.getCourseId(), userId, role);
        return toFullResponse(topic, new ArrayList<>());
    }

    public TopicWithLessonsResponse getFull(Long topicId, Long userId, String role) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> ApiBusinessException.notFound("Topic", topicId));
        courseAccessChecker.checkAccess(topic.getCourseId(), userId, role);
        return toFullResponse(topic, new ArrayList<>());
    }

    @Transactional
    public TopicResponse update(Long topicId, TopicRequest request, Long userId, String role) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> ApiBusinessException.notFound("Topic", topicId));
        courseAccessChecker.checkAccess(topic.getCourseId(), userId, role);
        if (request.title() != null) {
            topic.setTitle(request.title());
        }
        if (request.description() != null) {
            topic.setDescription(request.description());
        }
        if (request.orderIndex() != null) {
            topic.setOrderIndex(request.orderIndex());
        }
        return toResponse(topicRepository.save(topic));
    }

    @Transactional
    public void delete(Long topicId, Long userId, String role) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> ApiBusinessException.notFound("Topic", topicId));
        courseAccessChecker.checkAccess(topic.getCourseId(), userId, role);
        topicRepository.delete(topic);
    }

    @Transactional
    public LessonResponse createLessonInTopic(Long topicId, LessonRequest request, Long userId, String role) {
        return lessonService.createInTopic(topicId, request, userId, role);
    }

    @Transactional
    public List<TopicResponse> reorder(Long courseId, List<Long> order, Long userId, String role) {
        courseAccessChecker.checkAccess(courseId, userId, role);
        List<Topic> existing = topicRepository.findByCourseIdOrderByOrderIndex(courseId);
        if (order.size() != existing.size()
                || !new HashSet<>(order).equals(
                        existing.stream().map(Topic::getId).collect(Collectors.toSet()))) {
            throw ApiBusinessException.badRequest("Order list does not match existing topics");
        }
        for (int i = 0; i < order.size(); i++) {
            final Long topicId = order.get(i);
            final int newIndex = i;
            existing.stream()
                    .filter(t -> t.getId().equals(topicId))
                    .findFirst()
                    .ifPresent(t -> t.setOrderIndex(newIndex));
        }
        return topicRepository.saveAll(existing)
                .stream()
                .sorted((a, b) -> Integer.compare(a.getOrderIndex(), b.getOrderIndex()))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private TopicResponse toResponse(Topic t) {
        return new TopicResponse(
                t.getId(),
                t.getCourseId(),
                t.getTitle(),
                t.getDescription(),
                t.getOrderIndex(),
                t.getGlobalOrder(),
                t.getCreatedAt()
        );
    }

    private TopicWithLessonsResponse toFullResponse(Topic t, List<LessonSummaryResponse> lessons) {
        return new TopicWithLessonsResponse(
                t.getId(),
                t.getCourseId(),
                t.getTitle(),
                t.getDescription(),
                t.getOrderIndex(),
                t.getGlobalOrder(),
                t.getCreatedAt(),
                lessons
        );
    }
}

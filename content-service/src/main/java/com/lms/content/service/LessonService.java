package com.lms.content.service;

import com.lms.content.dto.lesson.GrantAccessRequest;
import com.lms.content.dto.lesson.LessonAccessEntryResponse;
import com.lms.content.dto.lesson.LessonAccessResponse;
import com.lms.content.dto.lesson.LessonRequest;
import com.lms.content.dto.lesson.LessonResponse;
import com.lms.content.dto.lesson.LessonWithContentResponse;
import com.lms.content.dto.lesson.TaskOrderResponse;
import com.lms.content.entity.Lesson;
import com.lms.content.entity.LessonAccess;
import com.lms.content.entity.Task;
import com.lms.content.entity.Topic;
import com.lms.content.exception.ApiBusinessException;
import com.lms.content.repository.CourseRepository;
import com.lms.content.repository.LessonAccessRepository;
import com.lms.content.repository.LessonRepository;
import com.lms.content.repository.TaskRepository;
import com.lms.content.repository.TopicRepository;
import com.lms.content.util.CourseAccessChecker;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LessonService {

    private final LessonRepository lessonRepository;
    private final LessonAccessRepository lessonAccessRepository;
    private final TopicRepository topicRepository;
    private final CourseRepository courseRepository;
    private final CourseAccessChecker courseAccessChecker;
    private final TaskRepository taskRepository;

    public LessonService(LessonRepository lessonRepository,
            LessonAccessRepository lessonAccessRepository,
            TopicRepository topicRepository,
            CourseRepository courseRepository,
            CourseAccessChecker courseAccessChecker,
            TaskRepository taskRepository) {
        this.lessonRepository = lessonRepository;
        this.lessonAccessRepository = lessonAccessRepository;
        this.topicRepository = topicRepository;
        this.courseRepository = courseRepository;
        this.courseAccessChecker = courseAccessChecker;
        this.taskRepository = taskRepository;
    }

    public List<LessonResponse> listByCourse(Long courseId, Long userId, String role) {
        courseAccessChecker.checkAccess(courseId, userId, role);
        return lessonRepository.findByCourseIdOrderByOrderIndex(courseId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<LessonResponse> listByTopic(Long topicId, Long userId, String role) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> ApiBusinessException.notFound("Topic", topicId));
        courseAccessChecker.checkAccess(topic.getCourseId(), userId, role);
        return lessonRepository.findByTopicIdOrderByOrderIndex(topicId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public LessonResponse create(Long courseId, LessonRequest request, Long userId, String role) {
        courseAccessChecker.checkAccess(courseId, userId, role);
        Lesson lesson = new Lesson();
        lesson.setCourseId(courseId);
        lesson.setTitle(request.title());
        lesson.setStatus("DRAFT");
        lesson.setUnlockMode(request.unlockMode() != null ? request.unlockMode() : "FREE");
        lesson.setVisible(request.visible() != null ? request.visible() : true);
        lesson.setPublishMode(request.publishMode());
        if (request.topicId() != null) {
            lesson.setTopicId(request.topicId());
            int nextIndex = lessonRepository.findMaxOrderIndexByTopicId(request.topicId())
                    .map(max -> max + 1).orElse(0);
            lesson.setOrderIndex(nextIndex);
        } else {
            int nextIndex = lessonRepository.findMaxOrderIndexByCourseId(courseId)
                    .map(max -> max + 1).orElse(0);
            lesson.setOrderIndex(nextIndex);
        }
        return toResponse(lessonRepository.save(lesson));
    }

    @Transactional
    public LessonResponse createInTopic(Long topicId, LessonRequest request, Long userId, String role) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> ApiBusinessException.notFound("Topic", topicId));
        courseAccessChecker.checkAccess(topic.getCourseId(), userId, role);
        Lesson lesson = new Lesson();
        lesson.setCourseId(topic.getCourseId());
        lesson.setTopicId(topicId);
        lesson.setTitle(request.title());
        lesson.setStatus("DRAFT");
        lesson.setUnlockMode(request.unlockMode() != null ? request.unlockMode() : "FREE");
        lesson.setVisible(request.visible() != null ? request.visible() : true);
        lesson.setPublishMode(request.publishMode());
        int nextIndex = lessonRepository.findMaxOrderIndexByTopicId(topicId)
                .map(max -> max + 1).orElse(0);
        lesson.setOrderIndex(nextIndex);
        return toResponse(lessonRepository.save(lesson));
    }

    public LessonWithContentResponse getById(Long lessonId, Long userId, String role) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> ApiBusinessException.notFound("Lesson", lessonId));
        courseAccessChecker.checkAccess(lesson.getCourseId(), userId, role);
        return toWithContent(lesson);
    }

    @Transactional
    public LessonResponse update(Long lessonId, LessonRequest request, Long userId, String role) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> ApiBusinessException.notFound("Lesson", lessonId));
        courseAccessChecker.checkAccess(lesson.getCourseId(), userId, role);
        if (request.title() != null) lesson.setTitle(request.title());
        if (request.topicId() != null) lesson.setTopicId(request.topicId());
        if (request.unlockMode() != null) lesson.setUnlockMode(request.unlockMode());
        if (request.visible() != null) lesson.setVisible(request.visible());
        if (request.publishMode() != null) lesson.setPublishMode(request.publishMode());
        return toResponse(lessonRepository.save(lesson));
    }

    @Transactional
    public void delete(Long lessonId, Long userId, String role) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> ApiBusinessException.notFound("Lesson", lessonId));
        courseAccessChecker.checkAccess(lesson.getCourseId(), userId, role);
        lessonRepository.delete(lesson);
    }

    @Transactional
    public LessonResponse publish(Long lessonId, boolean confirmed, Long userId, String role) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> ApiBusinessException.notFound("Lesson", lessonId));
        courseAccessChecker.checkAccess(lesson.getCourseId(), userId, role);

        String effectiveMode = lesson.getPublishMode();
        if (effectiveMode == null) {
            effectiveMode = courseRepository.findById(lesson.getCourseId())
                    .orElseThrow(() -> ApiBusinessException.notFound("Course", lesson.getCourseId()))
                    .getPublishMode();
        }

        if ("REVIEW".equals(effectiveMode) && "AI_GENERATED".equals(lesson.getStatus()) && !confirmed) {
            throw new ApiBusinessException("REVIEW_REQUIRED", 409,
                    "Lesson must be reviewed before publishing");
        }

        lesson.setStatus("PUBLISHED");
        lesson.setPublishedAt(Instant.now());
        return toResponse(lessonRepository.save(lesson));
    }

    @Transactional
    public List<TaskOrderResponse> reorderTasks(Long lessonId, List<Long> order, Long userId, String role) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> ApiBusinessException.notFound("Lesson", lessonId));
        courseAccessChecker.checkAccess(lesson.getCourseId(), userId, role);

        List<Task> existing = taskRepository.findByLessonId(lessonId);
        if (order.size() != existing.size()
                || !new HashSet<>(order).equals(
                        existing.stream().map(Task::getId).collect(Collectors.toSet()))) {
            throw ApiBusinessException.badRequest("Order list does not match lesson tasks");
        }

        for (int i = 0; i < order.size(); i++) {
            final Long taskId = order.get(i);
            final int newIndex = i;
            existing.stream()
                    .filter(t -> t.getId().equals(taskId))
                    .findFirst()
                    .ifPresent(t -> t.setOrderIndex(newIndex));
        }
        taskRepository.saveAll(existing);

        return order.stream()
                .map(taskId -> {
                    int idx = order.indexOf(taskId);
                    return new TaskOrderResponse(taskId, idx);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public LessonAccessResponse grantAccess(Long lessonId, Long studentId, Long userId, String role) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> ApiBusinessException.notFound("Lesson", lessonId));
        courseAccessChecker.checkAccess(lesson.getCourseId(), userId, role);
        LessonAccess access = new LessonAccess(lessonId, studentId);
        lessonAccessRepository.save(access);
        return new LessonAccessResponse(lessonId, studentId, access.getGrantedAt());
    }

    @Transactional
    public void revokeAccess(Long lessonId, Long studentId, Long userId, String role) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> ApiBusinessException.notFound("Lesson", lessonId));
        courseAccessChecker.checkAccess(lesson.getCourseId(), userId, role);
        lessonAccessRepository.deleteByIdLessonIdAndIdStudentId(lessonId, studentId);
    }

    public List<LessonAccessEntryResponse> listAccess(Long lessonId, Long userId, String role) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> ApiBusinessException.notFound("Lesson", lessonId));
        courseAccessChecker.checkAccess(lesson.getCourseId(), userId, role);
        return lessonAccessRepository.findByIdLessonId(lessonId)
                .stream()
                .map(la -> new LessonAccessEntryResponse(la.getStudentId(), la.getGrantedAt()))
                .collect(Collectors.toList());
    }

    private LessonResponse toResponse(Lesson l) {
        return new LessonResponse(
                l.getId(), l.getCourseId(), l.getTopicId(),
                l.getOrderIndex(), l.getGlobalOrder(),
                l.getTitle(), l.getStatus(),
                l.getPublishMode(), l.getUnlockMode(),
                l.isVisible(), 0, 0,
                l.getCreatedAt(), l.getPublishedAt());
    }

    private LessonWithContentResponse toWithContent(Lesson l) {
        return new LessonWithContentResponse(
                l.getId(), l.getCourseId(), l.getTopicId(),
                l.getOrderIndex(), l.getGlobalOrder(),
                l.getTitle(), l.getStatus(),
                l.getPublishMode(), l.getUnlockMode(),
                l.isVisible(), 0, 0,
                l.getCreatedAt(), l.getPublishedAt(),
                List.of(), List.of());
    }
}

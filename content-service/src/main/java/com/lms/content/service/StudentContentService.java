package com.lms.content.service;

import com.lms.content.client.LearningResultsClient;
import com.lms.content.client.LearningStatsClient;
import com.lms.content.client.dto.TaskResultSummary;
import com.lms.content.dto.block.BlockResponse;
import com.lms.content.dto.lesson.LessonWithContentResponse;
import com.lms.content.dto.student.StudentCourseResponse;
import com.lms.content.dto.student.StudentCourseTreeResponse;
import com.lms.content.dto.student.StudentCourseTreeResponse.LessonNode;
import com.lms.content.dto.student.StudentCourseTreeResponse.TopicNode;
import com.lms.content.dto.student.StudentTaskAvailabilityResponse;
import com.lms.content.dto.task.TaskResponse;
import com.lms.content.entity.Course;
import com.lms.content.entity.Lesson;
import com.lms.content.entity.Task;
import com.lms.content.entity.Topic;
import com.lms.content.exception.ApiBusinessException;
import com.lms.content.repository.CourseRepository;
import com.lms.content.repository.LessonBlockRepository;
import com.lms.content.repository.LessonRepository;
import com.lms.content.repository.TaskRepository;
import com.lms.content.repository.TopicRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StudentContentService {

    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final LessonBlockRepository lessonBlockRepository;
    private final TaskRepository taskRepository;
    private final TopicRepository topicRepository;
    private final LearningResultsClient learningResultsClient;
    private final LearningStatsClient learningStatsClient;

    public StudentContentService(CourseRepository courseRepository,
            LessonRepository lessonRepository,
            LessonBlockRepository lessonBlockRepository,
            TaskRepository taskRepository,
            TopicRepository topicRepository,
            LearningResultsClient learningResultsClient,
            LearningStatsClient learningStatsClient) {
        this.courseRepository = courseRepository;
        this.lessonRepository = lessonRepository;
        this.lessonBlockRepository = lessonBlockRepository;
        this.taskRepository = taskRepository;
        this.topicRepository = topicRepository;
        this.learningResultsClient = learningResultsClient;
        this.learningStatsClient = learningStatsClient;
    }

    public List<StudentCourseResponse> listCourses(Long studentId) {
        Page<Course> page = courseRepository.findAllByStudentId(studentId, PageRequest.of(0, 200));
        return page.stream()
                .map(c -> {
                    int lessonCount = lessonRepository.findByCourseIdOrderByOrderIndex(c.getId()).size();
                    return new StudentCourseResponse(
                            c.getId(), c.getTitle(), c.getDescription(),
                            c.getLevel(), c.getAccessStatus(),
                            lessonCount, 0, 0.0, c.getCreatedAt());
                })
                .collect(Collectors.toList());
    }

    public StudentCourseTreeResponse getCourseTree(Long courseId, Long studentId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> ApiBusinessException.notFound("Course", courseId));
        if (!courseRepository.existsByIdAndStudentId(courseId, studentId)) {
            throw ApiBusinessException.forbidden();
        }
        List<Topic> topics = topicRepository.findByCourseIdOrderByOrderIndex(courseId);
        List<Object[]> lessonRows = lessonRepository.findLessonsWithCountsByCourseId(courseId);
        Map<Long, List<Object[]>> byTopic = new LinkedHashMap<>();
        for (Object[] row : lessonRows) {
            Long topicId = row[1] != null ? ((Number) row[1]).longValue() : null;
            if (topicId == null) continue;
            byTopic.computeIfAbsent(topicId, k -> new ArrayList<>()).add(row);
        }
        boolean sequential = "SEQUENTIAL".equalsIgnoreCase(course.getAccessStatus());
        List<TopicNode> topicNodes = topics.stream()
                .map(t -> {
                    List<Object[]> rows = byTopic.getOrDefault(t.getId(), Collections.emptyList());
                    List<LessonNode> lessonNodes = new ArrayList<>();
                    for (int i = 0; i < rows.size(); i++) {
                        boolean locked = sequential && i > 0;
                        lessonNodes.add(toLessonNode(rows.get(i), locked));
                    }
                    return new TopicNode(t.getId(), t.getTitle(), t.getDescription(), t.getOrderIndex(), lessonNodes);
                })
                .collect(Collectors.toList());
        return new StudentCourseTreeResponse(
                course.getId(), course.getTitle(), course.getDescription(),
                course.getLevel(), course.getAccessStatus(), course.getAccessStatus(),
                topicNodes);
    }

    public LessonWithContentResponse getLesson(Long lessonId, Long studentId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> ApiBusinessException.notFound("Lesson", lessonId));
        if (!courseRepository.existsByIdAndStudentId(lesson.getCourseId(), studentId)) {
            throw ApiBusinessException.forbidden();
        }
        List<BlockResponse> blocks = lessonBlockRepository
                .findByLessonIdOrderByOrderIndex(lessonId).stream()
                .map(b -> new BlockResponse(b.getId(), b.getLessonId(), b.getOrderIndex(),
                        b.getType(), b.getContentJson(), b.isAiGenerated(), b.getCreatedAt()))
                .collect(Collectors.toList());
        List<TaskResponse> tasks = taskRepository.findByLessonIdOrderByOrderIndex(lessonId).stream()
                .map(t -> new TaskResponse(t.getId(), t.getCourseId(), t.getLessonId(),
                        t.getType(), t.getTitle(), t.getContent(), t.getStatus(), t.getOrderIndex(),
                        t.getUnlockMode(), t.getPrerequisiteTaskId(), t.getRequiredScore(),
                        t.isAiGenerated(), t.getPromptTemplateId(), t.getCreatedAt(), t.getUpdatedAt()))
                .collect(Collectors.toList());
        return new LessonWithContentResponse(
                lesson.getId(), lesson.getCourseId(), lesson.getTopicId(),
                lesson.getOrderIndex(), lesson.getGlobalOrder(),
                lesson.getTitle(), lesson.getStatus(),
                lesson.getPublishMode(), lesson.getUnlockMode(),
                lesson.isVisible(), blocks.size(), tasks.size(),
                lesson.getCreatedAt(), lesson.getPublishedAt(),
                blocks, tasks);
    }

    public List<BlockResponse> getLessonBlocks(Long lessonId, Long studentId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> ApiBusinessException.notFound("Lesson", lessonId));
        if (!courseRepository.existsByIdAndStudentId(lesson.getCourseId(), studentId)) {
            throw ApiBusinessException.forbidden();
        }
        return lessonBlockRepository.findByLessonIdOrderByOrderIndex(lessonId).stream()
                .map(b -> new BlockResponse(b.getId(), b.getLessonId(), b.getOrderIndex(),
                        b.getType(), b.getContentJson(), b.isAiGenerated(), b.getCreatedAt()))
                .collect(Collectors.toList());
    }

    public List<TaskResponse> getLessonTasks(Long lessonId, Long studentId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> ApiBusinessException.notFound("Lesson", lessonId));
        if (!courseRepository.existsByIdAndStudentId(lesson.getCourseId(), studentId)) {
            throw ApiBusinessException.forbidden();
        }
        return taskRepository.findByLessonIdOrderByOrderIndex(lessonId).stream()
                .map(t -> new TaskResponse(t.getId(), t.getCourseId(), t.getLessonId(),
                        t.getType(), t.getTitle(), t.getContent(), t.getStatus(), t.getOrderIndex(),
                        t.getUnlockMode(), t.getPrerequisiteTaskId(), t.getRequiredScore(),
                        t.isAiGenerated(), t.getPromptTemplateId(), t.getCreatedAt(), t.getUpdatedAt()))
                .collect(Collectors.toList());
    }

    public List<StudentTaskAvailabilityResponse> getAvailableTasks(Long lessonId, Long studentId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> ApiBusinessException.notFound("Lesson", lessonId));
        if (!courseRepository.existsByIdAndStudentId(lesson.getCourseId(), studentId)) {
            throw ApiBusinessException.forbidden();
        }
        List<Task> tasks = taskRepository.findByLessonIdOrderByOrderIndex(lessonId);
        if (tasks.isEmpty()) return Collections.emptyList();
        boolean allFree = tasks.stream().allMatch(t -> "FREE".equals(t.getUnlockMode()));
        Map<Long, TaskResultSummary> resultMap;
        if (allFree) {
            resultMap = Collections.emptyMap();
        } else {
            Set<Long> ids = new HashSet<>();
            for (Task t : tasks) {
                ids.add(t.getId());
                if (t.getPrerequisiteTaskId() != null) ids.add(t.getPrerequisiteTaskId());
            }
            resultMap = learningResultsClient.fetchResults(ids, studentId);
        }
        return tasks.stream()
                .map(t -> toAvailability(t, tasks, resultMap))
                .collect(Collectors.toList());
    }

    private StudentTaskAvailabilityResponse toAvailability(Task task, List<Task> allTasks,
            Map<Long, TaskResultSummary> resultMap) {
        TaskResultSummary myResult = resultMap.get(task.getId());
        boolean locked;
        String lockReason = null;
        switch (task.getUnlockMode()) {
            case "SEQUENTIAL" -> {
                locked = allTasks.stream()
                        .filter(t -> t.getOrderIndex() < task.getOrderIndex())
                        .anyMatch(t -> {
                            TaskResultSummary r = resultMap.get(t.getId());
                            return r == null || !isCompleted(r.status());
                        });
                if (locked) lockReason = "locked_by_SEQUENTIAL";
            }
            case "PREREQUISITE" -> {
                TaskResultSummary prereq = resultMap.get(task.getPrerequisiteTaskId());
                int required = task.getRequiredScore() != null ? task.getRequiredScore() : 0;
                int score = prereq != null ? Math.max(
                        prereq.aiScore() != null ? prereq.aiScore() : 0,
                        prereq.teacherScore() != null ? prereq.teacherScore() : 0) : 0;
                locked = prereq == null || !isCompleted(prereq.status()) || score < required;
                if (locked) lockReason = "score_below_" + required;
            }
            default -> locked = false;
        }
        return new StudentTaskAvailabilityResponse(
                task.getId(), task.getTitle(), task.getType(), task.getOrderIndex(),
                task.getUnlockMode(), locked, lockReason,
                myResult != null ? myResult.status() : null,
                myResult != null ? Math.max(
                        myResult.aiScore() != null ? myResult.aiScore() : 0,
                        myResult.teacherScore() != null ? myResult.teacherScore() : 0) : null);
    }

    private boolean isCompleted(String status) {
        return "CHECKED".equals(status) || "VALIDATED_BY_TEACHER".equals(status);
    }

    private LessonNode toLessonNode(Object[] row, boolean locked) {
        Long lessonId = ((Number) row[0]).longValue();
        String title = (String) row[2];
        String status = (String) row[3];
        int orderIndex = ((Number) row[4]).intValue();
        String unlockMode = (String) row[5];
        int blocksCount = ((Number) row[7]).intValue();
        int tasksCount = ((Number) row[8]).intValue();
        return new LessonNode(lessonId, title, status, orderIndex, unlockMode, locked, blocksCount, tasksCount);
    }
}

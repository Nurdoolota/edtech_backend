package com.lms.content.service;

import com.lms.content.client.LearningResultsClient;
import com.lms.content.client.dto.TaskResultSummary;
import com.lms.content.dto.lesson.TaskAvailabilityResult;
import com.lms.content.entity.Lesson;
import com.lms.content.entity.Task;
import com.lms.content.exception.ApiBusinessException;
import com.lms.content.repository.LessonRepository;
import com.lms.content.repository.TaskRepository;
import com.lms.content.util.CourseAccessChecker;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AvailableTasksService {

    private final LessonRepository lessonRepository;
    private final TaskRepository taskRepository;
    private final CourseAccessChecker courseAccessChecker;
    private final LearningResultsClient learningResultsClient;

    public AvailableTasksService(LessonRepository lessonRepository,
            TaskRepository taskRepository,
            CourseAccessChecker courseAccessChecker,
            LearningResultsClient learningResultsClient) {
        this.lessonRepository = lessonRepository;
        this.taskRepository = taskRepository;
        this.courseAccessChecker = courseAccessChecker;
        this.learningResultsClient = learningResultsClient;
    }

    public List<TaskAvailabilityResult> compute(Long lessonId, Long studentIdParam,
            Long userId, String role) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> ApiBusinessException.notFound("Lesson", lessonId));

        Long studentId = resolveStudentId(studentIdParam, userId, role);
        checkAccess(lesson, userId, role);

        List<Task> tasks = taskRepository.findByLessonIdOrderByOrderIndex(lessonId);
        if (tasks.isEmpty()) {
            return Collections.emptyList();
        }

        boolean allFree = tasks.stream().allMatch(t -> "FREE".equals(t.getUnlockMode()));
        Map<Long, TaskResultSummary> resultMap;
        if (allFree) {
            resultMap = Collections.emptyMap();
        } else {
            Set<Long> taskIds = new HashSet<>();
            for (Task t : tasks) {
                taskIds.add(t.getId());
                if (t.getPrerequisiteTaskId() != null) {
                    taskIds.add(t.getPrerequisiteTaskId());
                }
            }
            resultMap = learningResultsClient.fetchResults(taskIds, studentId);
        }

        return tasks.stream()
                .map(task -> evaluate(task, tasks, resultMap))
                .collect(Collectors.toList());
    }

    private Long resolveStudentId(Long studentIdParam, Long userId, String role) {
        if ("STUDENT".equalsIgnoreCase(role)) {
            if (studentIdParam == null) {
                return userId;
            }
            if (!studentIdParam.equals(userId)) {
                throw ApiBusinessException.forbidden();
            }
            return studentIdParam;
        }
        if (studentIdParam == null) {
            throw ApiBusinessException.badRequest("studentId is required");
        }
        return studentIdParam;
    }

    private void checkAccess(Lesson lesson, Long userId, String role) {
        if ("ADMIN".equalsIgnoreCase(role)) {
            return;
        }
        if ("TEACHER".equalsIgnoreCase(role)) {
            courseAccessChecker.checkAccess(lesson.getCourseId(), userId, role);
        }
        // STUDENT access already validated in resolveStudentId
    }

    private TaskAvailabilityResult evaluate(Task task, List<Task> allTasks,
            Map<Long, TaskResultSummary> resultMap) {
        return switch (task.getUnlockMode()) {
            case "SEQUENTIAL" -> evaluateSequential(task, allTasks, resultMap);
            case "PREREQUISITE" -> evaluatePrerequisite(task, resultMap);
            default -> new TaskAvailabilityResult(task.getId(), true, null);
        };
    }

    private TaskAvailabilityResult evaluateSequential(Task task, List<Task> allTasks,
            Map<Long, TaskResultSummary> resultMap) {
        boolean allCompleted = allTasks.stream()
                .filter(t -> t.getOrderIndex() < task.getOrderIndex())
                .allMatch(t -> {
                    TaskResultSummary r = resultMap.get(t.getId());
                    return r != null && isCompleted(r.status());
                });
        return new TaskAvailabilityResult(task.getId(), allCompleted,
                allCompleted ? null : "locked_by_SEQUENTIAL");
    }

    private TaskAvailabilityResult evaluatePrerequisite(Task task,
            Map<Long, TaskResultSummary> resultMap) {
        TaskResultSummary prereqResult = resultMap.get(task.getPrerequisiteTaskId());
        int required = task.getRequiredScore() != null ? task.getRequiredScore() : 0;
        int effective = 0;
        if (prereqResult != null) {
            effective = Math.max(
                    prereqResult.aiScore() != null ? prereqResult.aiScore() : 0,
                    prereqResult.teacherScore() != null ? prereqResult.teacherScore() : 0);
        }
        boolean met = prereqResult != null
                && isCompleted(prereqResult.status())
                && effective >= required;
        return new TaskAvailabilityResult(task.getId(), met,
                met ? null : "score_below_" + required);
    }

    private boolean isCompleted(String status) {
        return "CHECKED".equals(status) || "VALIDATED_BY_TEACHER".equals(status);
    }
}

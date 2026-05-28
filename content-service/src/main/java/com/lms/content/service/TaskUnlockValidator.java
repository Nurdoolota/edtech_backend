package com.lms.content.service;

import com.lms.content.entity.Task;
import com.lms.content.exception.ApiBusinessException;
import com.lms.content.repository.TaskRepository;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class TaskUnlockValidator {

    private final TaskRepository taskRepository;

    public TaskUnlockValidator(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public void validate(Task task, String unlockMode, Long prerequisiteTaskId, Integer requiredScore) {
        if (unlockMode == null || "FREE".equals(unlockMode)) {
            return;
        }
        if ("SEQUENTIAL".equals(unlockMode)) {
            if (prerequisiteTaskId != null) {
                throw new ApiBusinessException("INVALID_UNLOCK_CONFIG", 400,
                        "SEQUENTIAL mode does not use prerequisiteTaskId");
            }
            if (requiredScore != null) {
                throw new ApiBusinessException("INVALID_UNLOCK_CONFIG", 400,
                        "SEQUENTIAL mode does not use requiredScore");
            }
            return;
        }
        if ("PREREQUISITE".equals(unlockMode)) {
            if (prerequisiteTaskId == null) {
                return;
            }
            if (requiredScore != null && (requiredScore < 0 || requiredScore > 100)) {
                throw ApiBusinessException.badRequest("requiredScore must be between 0 and 100");
            }
            Task prereq = taskRepository.findById(prerequisiteTaskId)
                    .orElseThrow(() -> ApiBusinessException.notFound("Task", prerequisiteTaskId));
            if (!prereq.getLessonId().equals(task.getLessonId())) {
                throw new ApiBusinessException("PREREQUISITE_NOT_IN_SAME_LESSON", 400,
                        "Prerequisite task must be in the same lesson");
            }
            if (hasCycle(task.getId(), prerequisiteTaskId)) {
                throw new ApiBusinessException("CYCLE_DETECTED", 400,
                        "Circular prerequisite dependency detected");
            }
        }
    }

    private boolean hasCycle(Long currentTaskId, Long proposedPrereqId) {
        Set<Long> visited = new HashSet<>();
        Long cursor = proposedPrereqId;
        while (cursor != null) {
            if (cursor.equals(currentTaskId)) return true;
            if (!visited.add(cursor)) return false;
            Task t = taskRepository.findById(cursor).orElse(null);
            if (t == null) return false;
            cursor = t.getPrerequisiteTaskId();
        }
        return false;
    }
}

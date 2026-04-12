package com.lms.content.service;

import com.lms.content.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies that a student may work on a task: task exists and the student belongs to a group
 * that has the task's course assigned ({@code group_courses} + {@code user_groups}).
 */
@Service
@Transactional(readOnly = true)
public class LearningAccessService {

    private final TaskRepository taskRepository;

    public LearningAccessService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public boolean hasStudentAccessToTask(Long studentId, Long taskId) {
        if (studentId == null || taskId == null) {
            return false;
        }
        if (!taskRepository.existsById(taskId)) {
            return false;
        }
        return taskRepository.studentHasAccessToTask(studentId, taskId);
    }
}

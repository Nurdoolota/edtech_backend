package com.lms.learning.service;

import com.lms.learning.entity.ResultStatus;
import com.lms.learning.entity.TaskInfo;
import com.lms.learning.entity.TaskResult;
import com.lms.learning.exception.TaskLockedException;
import com.lms.learning.repository.TaskInfoRepository;
import com.lms.learning.repository.TaskResultRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UnlockEnforcementService {

    private static final String FREE = "FREE";
    private static final String SEQUENTIAL = "SEQUENTIAL";
    private static final String PREREQUISITE = "PREREQUISITE";

    private final TaskInfoRepository taskInfoRepository;
    private final TaskResultRepository taskResultRepository;

    public UnlockEnforcementService(TaskInfoRepository taskInfoRepository,
            TaskResultRepository taskResultRepository) {
        this.taskInfoRepository = taskInfoRepository;
        this.taskResultRepository = taskResultRepository;
    }

    @Transactional(readOnly = true)
    public void checkUnlock(Long taskId, Long studentId) {
        Optional<TaskInfo> infoOpt = taskInfoRepository.findById(taskId);
        if (infoOpt.isEmpty()) {
            return;
        }
        TaskInfo task = infoOpt.get();

        if (task.getUnlockMode() == null || FREE.equals(task.getUnlockMode())) {
            return;
        }

        if (SEQUENTIAL.equals(task.getUnlockMode())) {
            checkSequential(task, studentId);
        } else if (PREREQUISITE.equals(task.getUnlockMode())) {
            checkPrerequisite(task, studentId);
        }
    }

    private void checkSequential(TaskInfo task, Long studentId) {
        List<TaskInfo> predecessors = taskInfoRepository
                .findByLessonIdAndOrderIndexLessThan(task.getLessonId(), task.getOrderIndex());

        for (TaskInfo predecessor : predecessors) {
            Optional<TaskResult> result = taskResultRepository
                    .findByStudentIdAndTaskId(studentId, predecessor.getId());
            if (result.isEmpty() || !isCompleted(result.get().getStatus())) {
                throw new TaskLockedException("locked_by_SEQUENTIAL");
            }
        }
    }

    private void checkPrerequisite(TaskInfo task, Long studentId) {
        Long prereqId = task.getPrerequisiteTaskId();
        if (prereqId == null) {
            return;
        }
        int requiredScore = task.getRequiredScore() != null ? task.getRequiredScore() : 0;

        Optional<TaskResult> prereqResultOpt = taskResultRepository
                .findByStudentIdAndTaskId(studentId, prereqId);

        if (prereqResultOpt.isEmpty()) {
            throw new TaskLockedException("score_below_" + requiredScore);
        }

        TaskResult prereq = prereqResultOpt.get();
        int effectiveScore = Math.max(
                prereq.getAiScore() != null ? prereq.getAiScore() : 0,
                prereq.getTeacherScore() != null ? prereq.getTeacherScore() : 0);

        if (!isCompleted(prereq.getStatus()) || effectiveScore < requiredScore) {
            throw new TaskLockedException("score_below_" + requiredScore);
        }
    }

    private static boolean isCompleted(ResultStatus status) {
        return status == ResultStatus.CHECKED || status == ResultStatus.VALIDATED_BY_TEACHER;
    }
}

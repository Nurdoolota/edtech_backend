package com.lms.learning.service;

import com.lms.learning.dto.StudentStatsDto;
import com.lms.learning.dto.TaskResultSummaryDto;
import com.lms.learning.entity.TaskResult;
import com.lms.learning.repository.TaskResultRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InternalLearningService {

    private final TaskResultRepository taskResultRepository;

    public InternalLearningService(TaskResultRepository taskResultRepository) {
        this.taskResultRepository = taskResultRepository;
    }

    @Transactional(readOnly = true)
    public List<TaskResultSummaryDto> getResultSummaries(Set<Long> taskIds, Long studentId) {
        Map<Long, TaskResult> resultMap = taskResultRepository
                .findByTaskIdInAndStudentId(taskIds, studentId)
                .stream()
                .collect(Collectors.toMap(TaskResult::getTaskId, r -> r));

        return taskIds.stream()
                .map(taskId -> {
                    TaskResult r = resultMap.get(taskId);
                    return new TaskResultSummaryDto(
                            taskId,
                            r != null ? r.getStatus() : null,
                            r != null ? r.getAiScore() : null,
                            r != null ? r.getTeacherScore() : null);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public StudentStatsDto getStudentStats(Long studentId) {
        long tasksSubmitted = taskResultRepository.countByStudentId(studentId);
        Double averageScore = taskResultRepository.findAverageScoreByStudentId(studentId);
        var lastActivity = taskResultRepository.findLatestCreatedAtByStudentId(studentId);
        return new StudentStatsDto(tasksSubmitted, averageScore, lastActivity);
    }
}

package com.lms.learning.service;

import com.lms.learning.dto.PagedResponse;
import com.lms.learning.dto.SubmitAnswerRequest;
import com.lms.learning.dto.TaskResultResponse;
import com.lms.learning.dto.ValidateResultRequest;
import com.lms.learning.entity.ResultStatus;
import com.lms.learning.entity.Task;
import com.lms.learning.entity.TaskResult;
import com.lms.learning.exception.ApiBusinessException;
import com.lms.learning.repository.TaskRepository;
import com.lms.learning.repository.TaskResultRepository;
import com.lms.learning.service.checker.EvaluationResult;
import com.lms.learning.service.checker.TaskCheckerFactory;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningService {

    private final TaskRepository taskRepository;
    private final TaskResultRepository taskResultRepository;
    private final TaskCheckerFactory checkerFactory;
    private final AccessCheckService accessCheckService;
    private final TaskResultMapper mapper;

    public LearningService(TaskRepository taskRepository,
            TaskResultRepository taskResultRepository,
            TaskCheckerFactory checkerFactory,
            AccessCheckService accessCheckService,
            TaskResultMapper mapper) {
        this.taskRepository = taskRepository;
        this.taskResultRepository = taskResultRepository;
        this.checkerFactory = checkerFactory;
        this.accessCheckService = accessCheckService;
        this.mapper = mapper;
    }

    @Transactional
    public TaskResultResponse submitAnswer(Long taskId, Long studentId,
            SubmitAnswerRequest request) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> ApiBusinessException.notFound("Task", taskId));

        accessCheckService.assertStudentHasAccess(studentId, taskId);

        Optional<TaskResult> existing = taskResultRepository.findByStudentIdAndTaskId(studentId, taskId);
        if (existing.isPresent() && existing.get().getStatus() == ResultStatus.VALIDATED_BY_TEACHER) {
            throw ApiBusinessException.conflict(
                    "This task has already been validated by a teacher and cannot be resubmitted.");
        }

        EvaluationResult evaluation = checkerFactory.getChecker(task.getType())
                .check(task, request.answerContent());

        TaskResult result = existing.orElseGet(TaskResult::new);
        result.setTaskId(taskId);
        result.setStudentId(studentId);
        result.setAnswerContent(request.answerContent());
        result.setScore(evaluation.score());
        result.setAiFeedback(evaluation.feedback());
        result.setStatus(ResultStatus.CHECKED);

        return mapper.toResponse(taskResultRepository.save(result));
    }

    @Transactional(readOnly = true)
    public PagedResponse<TaskResultResponse> getMyResults(Long studentId, Long courseId,
            Pageable pageable) {
        Page<TaskResult> page = courseId != null
                ? taskResultRepository.findByStudentIdAndCourseId(studentId, courseId, pageable)
                : taskResultRepository.findByStudentId(studentId, pageable);
        return toPagedResponse(page);
    }

    @Transactional(readOnly = true)
    public PagedResponse<TaskResultResponse> getTaskResults(Long taskId, Pageable pageable) {
        if (!taskRepository.existsById(taskId)) {
            throw ApiBusinessException.notFound("Task", taskId);
        }
        return toPagedResponse(taskResultRepository.findByTaskId(taskId, pageable));
    }

    @Transactional
    public TaskResultResponse validateResult(Long resultId, ValidateResultRequest request) {
        TaskResult result = taskResultRepository.findById(resultId)
                .orElseThrow(() -> ApiBusinessException.notFound("TaskResult", resultId));

        result.setScore(request.score());
        if (request.comment() != null && !request.comment().isBlank()) {
            result.setAiFeedback(request.comment());
        }
        result.setStatus(ResultStatus.VALIDATED_BY_TEACHER);

        return mapper.toResponse(taskResultRepository.save(result));
    }

    private PagedResponse<TaskResultResponse> toPagedResponse(Page<TaskResult> page) {
        return new PagedResponse<>(
                page.getContent().stream().map(mapper::toResponse).toList(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}

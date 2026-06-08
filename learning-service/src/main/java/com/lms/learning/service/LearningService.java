package com.lms.learning.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.learning.dto.PagedResponse;
import com.lms.learning.dto.SubmitAnswerRequest;
import com.lms.learning.dto.TaskResultResponse;
import com.lms.learning.dto.ValidateResultRequest;
import com.lms.learning.entity.ResultStatus;
import com.lms.learning.entity.Task;
import com.lms.learning.entity.TaskType;
import com.lms.learning.entity.TaskResult;
import com.lms.learning.exception.ApiBusinessException;
import com.lms.learning.repository.TaskRepository;
import com.lms.learning.repository.TaskResultRepository;
import com.lms.learning.service.checker.EvaluationResult;
import com.lms.learning.service.checker.TaskCheckerFactory;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningService {


    /** Task types that require manual review; checker is never invoked for these. */
    private static final Set<TaskType> MANUAL_REVIEW_TYPES = Set.of(
            TaskType.SPEAKING, TaskType.LISTENING);

    private final TaskRepository taskRepository;
    private final TaskResultRepository taskResultRepository;
    private final TaskCheckerFactory checkerFactory;
    private final AccessCheckService accessCheckService;
    private final UnlockEnforcementService unlockEnforcementService;
    private final ObjectMapper objectMapper;
    private final TaskResultMapper mapper;

    public LearningService(TaskRepository taskRepository,
            TaskResultRepository taskResultRepository,
            TaskCheckerFactory checkerFactory,
            AccessCheckService accessCheckService,
            UnlockEnforcementService unlockEnforcementService,
            ObjectMapper objectMapper,
            TaskResultMapper mapper) {
        this.taskRepository = taskRepository;
        this.taskResultRepository = taskResultRepository;
        this.checkerFactory = checkerFactory;
        this.accessCheckService = accessCheckService;
        this.unlockEnforcementService = unlockEnforcementService;
        this.objectMapper = objectMapper;
        this.mapper = mapper;
    }

    @Transactional
    public TaskResultResponse submitAnswer(Long taskId, Long studentId,
            SubmitAnswerRequest request) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> ApiBusinessException.notFound("Task", taskId));

        accessCheckService.assertStudentHasAccess(studentId, taskId);

        unlockEnforcementService.checkUnlock(taskId, studentId);

        Optional<TaskResult> existing = taskResultRepository.findByStudentIdAndTaskId(studentId, taskId);
        if (existing.isPresent() && existing.get().getStatus() == ResultStatus.VALIDATED_BY_TEACHER) {
            throw ApiBusinessException.conflict(
                    "This task has already been validated by a teacher and cannot be resubmitted.");
        }

        TaskResult result = existing.orElseGet(TaskResult::new);
        result.setTaskId(taskId);
        result.setStudentId(studentId);

        if (MANUAL_REVIEW_TYPES.contains(task.getType())) {
            // Audio tasks: store submission for manual teacher review, no automated checking.
            result.setAnswerContent(request.answerContent() != null
                    ? request.answerContent()
                    : objectMapper.nullNode());
            result.setMediaId(request.mediaId());
            result.setTranscript(request.transcript());
            result.setAiScore(0);
            result.setScore(BigDecimal.ZERO);
            result.setStatus(ResultStatus.SUBMITTED);
        } else {
            validateAnswerContent(request.answerContent());
            String answerJson = writeAnswerJson(request.answerContent());

            EvaluationResult evaluation = checkerFactory.getChecker(task.getType())
                    .check(task, answerJson);

            int aiScore = clampToInt(evaluation.score());

            result.setAnswerContent(request.answerContent());
            result.setAiScore(aiScore);
            result.setAiBreakdown(evaluation.aiBreakdown());
            result.setAiFeedback(evaluation.feedback());
            result.setScore(effectiveScore(aiScore, result.getTeacherScore()));
            result.setStatus(ResultStatus.CHECKED);
            if (request.mediaId() != null) {
                result.setMediaId(request.mediaId());
            }
            if (request.transcript() != null) {
                result.setTranscript(request.transcript());
            }
        }

        return mapper.toResponse(taskResultRepository.save(result));
    }

    private void validateAnswerContent(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            throw ApiBusinessException.badRequest("answer_content is required");
        }
        if (node.isTextual() && node.asText().isBlank()) {
            throw ApiBusinessException.badRequest("answer_content must not be blank");
        }
    }

    private String writeAnswerJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw ApiBusinessException.badRequest("Invalid answer_content: " + e.getMessage());
        }
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

        BigDecimal scoreDecimal = request.score();
        if (scoreDecimal.compareTo(BigDecimal.ZERO) < 0
                || scoreDecimal.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw ApiBusinessException.validationError("score must be in [0, 100]");
        }

        int teacherScore = scoreDecimal.intValue();
        result.setTeacherScore(teacherScore);
        result.setTeacherFeedback(request.comment());
        result.setScore(effectiveScore(result.getAiScore(), teacherScore));
        result.setStatus(ResultStatus.VALIDATED_BY_TEACHER);

        return mapper.toResponse(taskResultRepository.save(result));
    }

    private PagedResponse<TaskResultResponse> toPagedResponse(Page<TaskResult> page) {
        return new PagedResponse<>(
                page.getContent().stream().map(mapper::toResponse).toList(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    private static BigDecimal effectiveScore(Integer aiScore, Integer teacherScore) {
        int ai  = aiScore      != null ? aiScore      : 0;
        int tch = teacherScore != null ? teacherScore : 0;
        return BigDecimal.valueOf(Math.max(ai, tch));
    }

    private static int clampToInt(BigDecimal score) {
        if (score == null) return 0;
        int v = (int) Math.round(score.doubleValue());
        return Math.max(0, Math.min(100, v));
    }
}

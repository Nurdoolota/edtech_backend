package com.lms.learning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.learning.dto.SubmitAnswerRequest;
import com.lms.learning.dto.TaskResultResponse;
import com.lms.learning.dto.ValidateResultRequest;
import com.lms.learning.entity.ResultStatus;
import com.lms.learning.entity.Task;
import com.lms.learning.entity.TaskResult;
import com.lms.learning.entity.TaskType;
import com.lms.learning.exception.ApiBusinessException;
import com.lms.learning.exception.TaskLockedException;
import com.lms.learning.repository.TaskRepository;
import com.lms.learning.repository.TaskResultRepository;
import com.lms.learning.service.checker.EvaluationResult;
import com.lms.learning.service.checker.KeyBasedChecker;
import com.lms.learning.service.UnlockEnforcementService;
import com.lms.learning.service.checker.TaskCheckerFactory;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LearningServiceTest {

    @Mock TaskRepository taskRepository;
    @Mock TaskResultRepository taskResultRepository;
    @Mock TaskCheckerFactory checkerFactory;
    @Mock AccessCheckService accessCheckService;
    @Mock UnlockEnforcementService unlockEnforcementService;
    @Mock KeyBasedChecker keyBasedChecker;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private TaskResultMapper mapper;
    private LearningService learningService;

    @BeforeEach
    void setUp() {
        mapper = new TaskResultMapper(objectMapper);
        learningService = new LearningService(taskRepository, taskResultRepository,
                checkerFactory, accessCheckService, unlockEnforcementService, objectMapper, mapper);
    }

    @Test
    void submitAnswer_newResult_savesAndReturns() throws Exception {
        Task task = fillBlanksTask();
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        doNothing().when(accessCheckService).assertStudentHasAccess(10L, 1L);
        when(taskResultRepository.findByStudentIdAndTaskId(10L, 1L)).thenReturn(Optional.empty());
        when(checkerFactory.getChecker(TaskType.FILL_BLANKS)).thenReturn(keyBasedChecker);
        when(keyBasedChecker.check(any(), any()))
                .thenReturn(new EvaluationResult(BigDecimal.valueOf(100), "Correct!", null));

        TaskResult saved = resultWith(ResultStatus.CHECKED, BigDecimal.valueOf(100));
        when(taskResultRepository.save(any())).thenReturn(saved);

        TaskResultResponse response = learningService.submitAnswer(1L, 10L,
                new SubmitAnswerRequest(objectMapper.readTree("[\"apple\"]")));

        assertThat(response.score()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(response.status()).isEqualTo(ResultStatus.CHECKED);
        verify(taskResultRepository).save(any(TaskResult.class));
    }

    @Test
    void submitAnswer_taskNotFound_throws404() throws Exception {
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                learningService.submitAnswer(99L, 10L,
                        new SubmitAnswerRequest(objectMapper.readTree("[]"))))
                .isInstanceOf(ApiBusinessException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void submitAnswer_noAccess_throws403() throws Exception {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(fillBlanksTask()));
        doThrow(ApiBusinessException.forbidden())
                .when(accessCheckService).assertStudentHasAccess(10L, 1L);

        assertThatThrownBy(() ->
                learningService.submitAnswer(1L, 10L,
                        new SubmitAnswerRequest(objectMapper.readTree("[]"))))
                .isInstanceOf(ApiBusinessException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void submitAnswer_taskLocked_throws403() throws Exception {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(fillBlanksTask()));
        doNothing().when(accessCheckService).assertStudentHasAccess(10L, 1L);
        doThrow(new TaskLockedException("locked_by_SEQUENTIAL"))
                .when(unlockEnforcementService).checkUnlock(1L, 10L);

        assertThatThrownBy(() ->
                learningService.submitAnswer(1L, 10L,
                        new SubmitAnswerRequest(objectMapper.readTree("[\"apple\"]"))))
                .isInstanceOf(TaskLockedException.class)
                .hasMessageContaining("locked_by_SEQUENTIAL");
    }

    @Test
    void submitAnswer_alreadyValidated_throws409() throws Exception {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(fillBlanksTask()));
        doNothing().when(accessCheckService).assertStudentHasAccess(10L, 1L);
        when(taskResultRepository.findByStudentIdAndTaskId(10L, 1L))
                .thenReturn(Optional.of(resultWith(ResultStatus.VALIDATED_BY_TEACHER, BigDecimal.valueOf(90))));

        assertThatThrownBy(() ->
                learningService.submitAnswer(1L, 10L,
                        new SubmitAnswerRequest(objectMapper.readTree("[]"))))
                .isInstanceOf(ApiBusinessException.class)
                .hasMessageContaining("validated");
    }

    @Test
    void validateResult_updatesScoreAndStatus() {
        TaskResult existing = resultWith(ResultStatus.CHECKED, BigDecimal.valueOf(70));
        existing.setId(5L);
        when(taskResultRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(taskResultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TaskResultResponse response = learningService.validateResult(5L,
                new ValidateResultRequest(BigDecimal.valueOf(95), "Well done"));

        assertThat(response.status()).isEqualTo(ResultStatus.VALIDATED_BY_TEACHER);
        assertThat(response.score()).isEqualByComparingTo(BigDecimal.valueOf(95));
    }

    @Test
    void submitAnswer_speakingTask_savesSubmittedWithoutChecker() throws Exception {
        Task task = audioTask(TaskType.SPEAKING);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        doNothing().when(accessCheckService).assertStudentHasAccess(10L, 1L);
        when(taskResultRepository.findByStudentIdAndTaskId(10L, 1L)).thenReturn(Optional.empty());

        TaskResult saved = resultWith(ResultStatus.SUBMITTED, BigDecimal.ZERO);
        when(taskResultRepository.save(any())).thenReturn(saved);

        TaskResultResponse response = learningService.submitAnswer(1L, 10L,
                new SubmitAnswerRequest(null, "media-123", "my transcript"));

        assertThat(response.status()).isEqualTo(ResultStatus.SUBMITTED);
        verify(checkerFactory, never()).getChecker(any());
    }

    @Test
    void submitAnswer_listeningTask_savesSubmittedWithoutChecker() throws Exception {
        Task task = audioTask(TaskType.LISTENING);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        doNothing().when(accessCheckService).assertStudentHasAccess(10L, 1L);
        when(taskResultRepository.findByStudentIdAndTaskId(10L, 1L)).thenReturn(Optional.empty());

        TaskResult saved = resultWith(ResultStatus.SUBMITTED, BigDecimal.ZERO);
        when(taskResultRepository.save(any())).thenReturn(saved);

        TaskResultResponse response = learningService.submitAnswer(1L, 10L,
                new SubmitAnswerRequest(null, null, null));

        assertThat(response.status()).isEqualTo(ResultStatus.SUBMITTED);
        verify(checkerFactory, never()).getChecker(any());
    }

    @Test
    void submitAnswer_readingComprehensionTask_callsAiChecker() throws Exception {
        Task task = audioTask(TaskType.READING_COMPREHENSION);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        doNothing().when(accessCheckService).assertStudentHasAccess(10L, 1L);
        when(taskResultRepository.findByStudentIdAndTaskId(10L, 1L)).thenReturn(Optional.empty());
        when(checkerFactory.getChecker(TaskType.READING_COMPREHENSION)).thenReturn(keyBasedChecker);
        when(keyBasedChecker.check(any(), any()))
                .thenReturn(new EvaluationResult(BigDecimal.valueOf(80), "Good", null));

        TaskResult saved = resultWith(ResultStatus.CHECKED, BigDecimal.valueOf(80));
        when(taskResultRepository.save(any())).thenReturn(saved);

        TaskResultResponse response = learningService.submitAnswer(1L, 10L,
                new SubmitAnswerRequest(objectMapper.readTree("\"some text\"")));

        assertThat(response.status()).isEqualTo(ResultStatus.CHECKED);
    }

    private Task audioTask(TaskType type) {
        return new Task() {
            @Override public Long getId() { return 1L; }
            @Override public TaskType getType() { return type; }
            @Override public java.util.Map<String, Object> getContent() { return java.util.Map.of(); }
        };
    }

    private Task fillBlanksTask() {
        return new Task() {
            @Override public Long getId() { return 1L; }
            @Override public TaskType getType() { return TaskType.FILL_BLANKS; }
            @Override public Map<String, Object> getContent() {
                return Map.of("text", "Fill ___", "answers", java.util.List.of("apple"));
            }
        };
    }

    private TaskResult resultWith(ResultStatus status, BigDecimal score) {
        TaskResult r = new TaskResult();
        r.setId(1L);
        r.setTaskId(1L);
        r.setStudentId(10L);
        try {
            r.setAnswerContent(objectMapper.readTree("[\"apple\"]"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        r.setScore(score);
        r.setAiFeedback("feedback");
        r.setStatus(status);
        return r;
    }
}

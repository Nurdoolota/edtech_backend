package com.lms.learning.service.checker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.learning.entity.Task;
import com.lms.learning.entity.TaskType;
import com.lms.learning.exception.ApiBusinessException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KeyBasedCheckerTest {

    private KeyBasedChecker checker;

    @BeforeEach
    void setUp() {
        checker = new KeyBasedChecker(new ObjectMapper());
    }

    @Test
    void fillBlanks_allCorrect_returns100() throws Exception {
        Task task = taskWith(TaskType.FILL_BLANKS,
                Map.of("text", "Fill ___", "answers", List.of("apple", "banana")));

        EvaluationResult result = checker.check(task, "[\"apple\",\"banana\"]");

        assertThat(result.score()).isEqualByComparingTo(BigDecimal.valueOf(100));
    }

    @Test
    void fillBlanks_wrongAnswer_returns0() throws Exception {
        Task task = taskWith(TaskType.FILL_BLANKS,
                Map.of("text", "Fill ___", "answers", List.of("apple", "banana")));

        EvaluationResult result = checker.check(task, "[\"apple\",\"wrong\"]");

        assertThat(result.score()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void fillBlanks_caseInsensitive_returns100() throws Exception {
        Task task = taskWith(TaskType.FILL_BLANKS,
                Map.of("text", "Fill ___", "answers", List.of("Apple")));

        EvaluationResult result = checker.check(task, "[\"APPLE\"]");

        assertThat(result.score()).isEqualByComparingTo(BigDecimal.valueOf(100));
    }

    @Test
    void fillBlanks_wrongCount_returns0() {
        Task task = taskWith(TaskType.FILL_BLANKS,
                Map.of("text", "Fill ___", "answers", List.of("apple", "banana")));

        EvaluationResult result = checker.check(task, "[\"apple\"]");

        assertThat(result.score()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void trueFalse_allCorrect_returns100() {
        Task task = taskWith(TaskType.TRUE_FALSE,
                Map.of("questions", List.of("Q1", "Q2"),
                        "correctOptions", List.of("true", "false")));

        EvaluationResult result = checker.check(task, "[\"true\",\"false\"]");

        assertThat(result.score()).isEqualByComparingTo(BigDecimal.valueOf(100));
    }

    @Test
    void trueFalse_wrongAnswer_returns0() {
        Task task = taskWith(TaskType.TRUE_FALSE,
                Map.of("questions", List.of("Q1"),
                        "correctOptions", List.of("true")));

        EvaluationResult result = checker.check(task, "[\"false\"]");

        assertThat(result.score()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void invalidJson_throwsBadRequest() {
        Task task = taskWith(TaskType.FILL_BLANKS,
                Map.of("text", "Fill", "answers", List.of("x")));

        assertThatThrownBy(() -> checker.check(task, "not-json"))
                .isInstanceOf(ApiBusinessException.class)
                .hasMessageContaining("Invalid answer format");
    }

    private Task taskWith(TaskType type, Map<String, Object> content) {
        Task task = new Task() {
            @Override
            public Long getId() { return 1L; }

            @Override
            public TaskType getType() { return type; }

            @Override
            public Map<String, Object> getContent() { return content; }
        };
        return task;
    }
}

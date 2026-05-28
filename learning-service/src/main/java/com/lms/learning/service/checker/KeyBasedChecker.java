package com.lms.learning.service.checker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.learning.entity.Task;
import com.lms.learning.entity.TaskType;
import com.lms.learning.exception.ApiBusinessException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Evaluates FILL_BLANKS and TRUE_FALSE tasks by comparing answers to stored answer keys.
 * Score is 100 if all answers match, 0 otherwise.
 */
@Component
public class KeyBasedChecker implements TaskChecker {

    private final ObjectMapper objectMapper;

    public KeyBasedChecker(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public EvaluationResult check(Task task, String answerContent) {
        Map<String, Object> content = task.getContent();
        try {
            if (task.getType() == TaskType.FILL_BLANKS) {
                return checkFillBlanks(content, answerContent);
            } else if (task.getType() == TaskType.TRUE_FALSE) {
                return checkTrueFalse(content, answerContent);
            }
            throw ApiBusinessException.badRequest("Unsupported task type for key-based checking: " + task.getType());
        } catch (ApiBusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ApiBusinessException.badRequest("Invalid answer format: " + ex.getMessage());
        }
    }

    private EvaluationResult checkFillBlanks(Map<String, Object> content, String answerContent)
            throws Exception {
        List<String> expectedAnswers = castToStringList(content.get("answers"));
        List<String> studentAnswers = objectMapper.readValue(
                answerContent, new TypeReference<List<String>>() {});

        if (studentAnswers.size() != expectedAnswers.size()) {
            return new EvaluationResult(BigDecimal.ZERO,
                    "Expected " + expectedAnswers.size() + " answers but got " + studentAnswers.size(), null);
        }
        for (int i = 0; i < expectedAnswers.size(); i++) {
            if (!expectedAnswers.get(i).equalsIgnoreCase(studentAnswers.get(i).trim())) {
                return new EvaluationResult(BigDecimal.ZERO,
                        "Incorrect. Expected: " + String.join(", ", expectedAnswers), null);
            }
        }
        return new EvaluationResult(BigDecimal.valueOf(100), "All answers correct.", null);
    }

    private EvaluationResult checkTrueFalse(Map<String, Object> content, String answerContent)
            throws Exception {
        List<String> correctOptions = castToStringList(content.get("correctOptions"));
        List<String> studentOptions = objectMapper.readValue(
                answerContent, new TypeReference<List<String>>() {});

        if (studentOptions.size() != correctOptions.size()) {
            return new EvaluationResult(BigDecimal.ZERO,
                    "Expected " + correctOptions.size() + " answers but got " + studentOptions.size(), null);
        }
        for (int i = 0; i < correctOptions.size(); i++) {
            if (!correctOptions.get(i).equalsIgnoreCase(studentOptions.get(i).trim())) {
                return new EvaluationResult(BigDecimal.ZERO, "One or more answers are incorrect.", null);
            }
        }
        return new EvaluationResult(BigDecimal.valueOf(100), "All answers correct.", null);
    }

    @SuppressWarnings("unchecked")
    private List<String> castToStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            throw ApiBusinessException.badRequest("Task content is missing required array field");
        }
        return (List<String>) list;
    }
}

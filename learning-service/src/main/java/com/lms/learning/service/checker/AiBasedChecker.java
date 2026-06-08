package com.lms.learning.service.checker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.learning.dto.ai.AiEvaluateRequest;
import com.lms.learning.dto.ai.AiEvaluateRequest.AiOptions;
import com.lms.learning.dto.ai.AiEvaluateResponse;
import com.lms.learning.entity.Task;
import com.lms.learning.entity.TaskType;
import com.lms.learning.exception.ApiBusinessException;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Evaluates TEXT, TRANSLATION, VIDEO, DEBATES, READING_COMPREHENSION, IMAGE_DESCRIPTION tasks
 * by delegating to AI Service.
 * Temperature: 0.2 for most types, 0.8 for DEBATES (configurable via task content).
 */
@Component
public class AiBasedChecker implements TaskChecker {

    private static final Logger log = LoggerFactory.getLogger(AiBasedChecker.class);

    private static final String AI_EVALUATE_PATH = "/internal/ai/evaluate";

    private final RestClient aiRestClient;
    private final ObjectMapper objectMapper;

    public AiBasedChecker(@Qualifier("aiRestClient") RestClient aiRestClient, ObjectMapper objectMapper) {
        this.aiRestClient = aiRestClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public EvaluationResult check(Task task, String answerContent) {
        double temperature = task.getType() == TaskType.DEBATES ? 0.8 : 0.2;

        AiEvaluateRequest request = new AiEvaluateRequest(
                task.getType().name(),
                resolvePromptCode(task),
                task.getContent(),
                answerContent,
                new AiOptions(temperature, 2048));

        AiEvaluateResponse response;
        try {
            response = aiRestClient.post()
                    .uri(AI_EVALUATE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiEvaluateResponse.class);
        } catch (RestClientException ex) {
            log.error("AI Service call failed for task {}: {}", task.getId(), ex.getMessage());
            throw ApiBusinessException.serviceUnavailable(
                    "Evaluation service temporarily unavailable. Please try again later.");
        }

        if (response == null) {
            throw ApiBusinessException.serviceUnavailable(
                    "Evaluation service returned an empty response.");
        }

        BigDecimal score = response.score() != null ? response.score() : BigDecimal.ZERO;
        String feedback = response.feedback() != null ? response.feedback() : "";
        String breakdown = serializeBreakdown(response);
        return new EvaluationResult(score, feedback, breakdown);
    }

    private String serializeBreakdown(AiEvaluateResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize AI response for breakdown: {}", ex.getMessage());
            return null;
        }
    }

    private String resolvePromptCode(Task task) {
        return switch (task.getType()) {
            case TEXT -> "text_evaluation";
            case TRANSLATION -> "translation_evaluation";
            case VIDEO -> "video_evaluation";
            case DEBATES -> "debates_evaluation";
            case READING_COMPREHENSION -> "reading_comprehension_evaluation";
            case IMAGE_DESCRIPTION -> "image_description_evaluation";
            default -> throw ApiBusinessException.badRequest(
                    "Unsupported task type for AI-based checking: " + task.getType());
        };
    }
}

package com.lms.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.ai.dto.AiEvaluateRequest;
import com.lms.ai.dto.AiEvaluateResponse;
import com.lms.ai.exception.ApiBusinessException;
import com.lms.ai.llm.LlmClient;
import com.lms.ai.prompt.PromptTemplateService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AiEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(AiEvaluationService.class);

    private static final int DEFAULT_MAX_TOKENS = 2048;
    private static final double DEFAULT_TEMPERATURE = 0.2;

    private final LlmClient llmClient;
    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper;

    public AiEvaluationService(LlmClient llmClient,
                                PromptTemplateService promptTemplateService,
                                ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.promptTemplateService = promptTemplateService;
        this.objectMapper = objectMapper;
    }

    public AiEvaluateResponse evaluate(AiEvaluateRequest request) {
        String systemPrompt = promptTemplateService.render(
                request.promptTemplateCode(),
                request.taskContent(),
                request.studentAnswer());

        double temperature = resolveTemperature(request);
        int maxTokens = resolveMaxTokens(request);

        log.info("Evaluating task [type={}, template={}, temperature={}]",
                request.taskType(), request.promptTemplateCode(), temperature);

        String rawContent = llmClient.complete(systemPrompt, temperature, maxTokens);

        return parseResponse(rawContent, request.taskType());
    }

    private AiEvaluateResponse parseResponse(String raw, String taskType) {
        String json = stripMarkdownFences(raw.trim());

        JsonNode node;
        try {
            node = objectMapper.readTree(json);
        } catch (Exception e) {
            log.error("Failed to parse LLM JSON response: {}", raw);
            throw ApiBusinessException.badGateway(
                    "LLM returned an invalid JSON response. Please retry.");
        }

        String feedback = textField(node, "feedback");

        if ("DEBATES".equalsIgnoreCase(taskType)) {
            BigDecimal score = parseDebatesScore(node);
            return new AiEvaluateResponse(score, feedback, null);
        }

        String verdict = textField(node, "verdict");
        BigDecimal score = "Accepted".equalsIgnoreCase(verdict)
                ? BigDecimal.ONE
                : BigDecimal.ZERO;
        return new AiEvaluateResponse(score, feedback, verdict);
    }

    /**
     * Strip ```json ... ``` or ``` ... ``` wrappers that some models add despite instructions.
     */
    static String stripMarkdownFences(String text) {
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline == -1) {
                return text;
            }
            String withoutOpening = text.substring(firstNewline + 1);
            if (withoutOpening.endsWith("```")) {
                withoutOpening = withoutOpening.substring(0, withoutOpening.length() - 3).trim();
            }
            return withoutOpening.trim();
        }
        return text;
    }

    private BigDecimal parseDebatesScore(JsonNode node) {
        JsonNode scoreNode = node.get("score");
        if (scoreNode == null || !scoreNode.isNumber()) {
            log.warn("DEBATES response missing numeric 'score' field; defaulting to 0");
            return BigDecimal.ZERO;
        }
        BigDecimal value = scoreNode.decimalValue();
        // clamp to [0.0, 1.0]
        if (value.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
        if (value.compareTo(BigDecimal.ONE) > 0) return BigDecimal.ONE;
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String textField(JsonNode node, String key) {
        JsonNode field = node.get(key);
        return (field != null && !field.isNull()) ? field.asText("") : "";
    }

    private double resolveTemperature(AiEvaluateRequest request) {
        if (request.options() != null) {
            return request.options().temperature();
        }
        return DEFAULT_TEMPERATURE;
    }

    private int resolveMaxTokens(AiEvaluateRequest request) {
        if (request.options() != null) {
            return request.options().maxTokens();
        }
        return DEFAULT_MAX_TOKENS;
    }
}

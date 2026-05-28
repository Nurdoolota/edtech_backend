package com.lms.ai.service;

import com.lms.ai.config.LlmProperties;
import com.lms.ai.dto.gen.AiGenerateLessonRequest;
import com.lms.ai.dto.gen.AiGenerateTaskRequest;
import com.lms.ai.dto.gen.AiGenerateTopicRequest;
import com.lms.ai.dto.gen.AiLessonJson;
import com.lms.ai.dto.gen.AiRegenerateLessonRequest;
import com.lms.ai.dto.gen.AiTaskJson;
import com.lms.ai.dto.gen.AiTopicJson;
import com.lms.ai.exception.AiServiceException;
import com.lms.ai.llm.LlmClient;
import com.lms.ai.prompt.PromptTemplateService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AiGenerationService {

    private static final double GENERATION_TEMPERATURE = 0.7;
    private static final int GENERATION_MAX_TOKENS = 4096;

    private final LlmClient llmClient;
    private final LlmProperties llmProperties;
    private final PromptTemplateService templateService;
    private final AiRetryExecutor retryExecutor;
    private final AiCallLogger callLogger;

    public AiGenerationService(LlmClient llmClient,
                               LlmProperties llmProperties,
                               PromptTemplateService templateService,
                               AiRetryExecutor retryExecutor,
                               AiCallLogger callLogger) {
        this.llmClient = llmClient;
        this.llmProperties = llmProperties;
        this.templateService = templateService;
        this.retryExecutor = retryExecutor;
        this.callLogger = callLogger;
    }

    public AiLessonJson generateLesson(AiGenerateLessonRequest req, Long userId) {
        Map<String, String> vars = new HashMap<>();
        vars.put("TOPIC", req.topic());
        vars.put("LEVEL", req.level());
        vars.put("LENGTH", req.length() != null ? req.length() : "medium");
        vars.put("TASK_TYPES", joinList(req.taskTypes()));
        vars.put("INCLUDE_THEORY", req.includeTheory() != null ? req.includeTheory().toString() : "true");
        vars.put("INSTRUCTIONS", req.instructions() != null ? req.instructions() : "");

        return execute("lesson_generation", vars, AiLessonJson.class, userId, "/internal/ai/generate-lesson");
    }

    public AiTaskJson generateTask(AiGenerateTaskRequest req, Long userId) {
        Map<String, String> vars = new HashMap<>();
        vars.put("TASK_TYPE", req.type());
        vars.put("CONTEXT", req.context() != null ? req.context() : "");
        vars.put("LEVEL", req.level());

        return execute("task_generation", vars, AiTaskJson.class, userId, "/internal/ai/generate-task");
    }

    public AiTopicJson generateTopic(AiGenerateTopicRequest req, Long userId) {
        Map<String, String> vars = new HashMap<>();
        vars.put("TOPIC_TITLE", req.topicTitle());
        vars.put("DESCRIPTION", req.description() != null ? req.description() : "");
        vars.put("LEVEL", req.level());
        vars.put("LESSON_COUNT", req.lessonCount().toString());

        return execute("topic_generation", vars, AiTopicJson.class, userId, "/internal/ai/generate-topic");
    }

    public AiLessonJson regenerateLesson(AiRegenerateLessonRequest req, Long userId) {
        Map<String, String> vars = new HashMap<>();
        vars.put("EXISTING_LESSON_JSON", req.lessonJson());
        vars.put("HINT", req.hint() != null ? req.hint() : "");
        vars.put("PRESERVE_IDS", req.preserveIds() != null
                ? req.preserveIds().stream().map(Object::toString).collect(Collectors.joining(","))
                : "");
        vars.put("TASK_TYPES", joinList(req.taskTypes()));

        return execute("lesson_regeneration", vars, AiLessonJson.class, userId, "/internal/ai/regenerate-lesson");
    }

    private <T> T execute(String template, Map<String, String> vars, Class<T> responseType,
                          Long userId, String endpoint) {
        long start = System.currentTimeMillis();
        String status = "SUCCESS";
        String error = null;
        try {
            String prompt = templateService.render(template, vars);
            return retryExecutor.executeWithRetry(
                    () -> stripFences(llmClient.complete(prompt, GENERATION_TEMPERATURE, GENERATION_MAX_TOKENS)),
                    responseType);
        } catch (AiServiceException e) {
            status = "ERROR";
            error = e.getMessage();
            throw e;
        } finally {
            int latencyMs = (int) (System.currentTimeMillis() - start);
            callLogger.log(new AiCallEntry(userId, endpoint, llmProperties.model(),
                    latencyMs, null, null, status, error));
        }
    }

    private static String stripFences(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (!trimmed.startsWith("```")) return trimmed;
        int newline = trimmed.indexOf('\n');
        if (newline == -1) return trimmed;
        String body = trimmed.substring(newline + 1);
        if (body.endsWith("```")) body = body.substring(0, body.length() - 3).trim();
        return body.trim();
    }

    private static String joinList(List<String> list) {
        return list != null ? String.join(", ", list) : "";
    }
}

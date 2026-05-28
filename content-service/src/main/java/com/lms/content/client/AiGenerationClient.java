package com.lms.content.client;

import com.lms.content.client.dto.AiGenerateLessonRequest;
import com.lms.content.client.dto.AiGenerateTaskRequest;
import com.lms.content.client.dto.AiGenerateTopicRequest;
import com.lms.content.client.dto.AiLessonJson;
import com.lms.content.client.dto.AiRegenerateLessonRequest;
import com.lms.content.client.dto.AiTaskJson;
import com.lms.content.client.dto.AiTopicJson;
import com.lms.content.exception.ApiBusinessException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class AiGenerationClient {

    private final RestTemplate aiRestTemplate;

    public AiGenerationClient(@Qualifier("aiRestTemplate") RestTemplate aiRestTemplate) {
        this.aiRestTemplate = aiRestTemplate;
    }

    public AiLessonJson generateLesson(AiGenerateLessonRequest request) {
        try {
            AiLessonJson result = aiRestTemplate.postForObject(
                    "/internal/ai/generate-lesson", request, AiLessonJson.class);
            if (result == null) {
                throw new ApiBusinessException("SERVICE_UNAVAILABLE", 503,
                        "AI service returned empty response");
            }
            return result;
        } catch (RestClientException e) {
            throw new ApiBusinessException("SERVICE_UNAVAILABLE", 503,
                    "AI service unavailable");
        }
    }

    public AiTaskJson generateTask(AiGenerateTaskRequest request) {
        try {
            AiTaskJson result = aiRestTemplate.postForObject(
                    "/internal/ai/generate-task", request, AiTaskJson.class);
            if (result == null) {
                throw new ApiBusinessException("SERVICE_UNAVAILABLE", 503,
                        "AI service returned empty response");
            }
            return result;
        } catch (RestClientException e) {
            throw new ApiBusinessException("SERVICE_UNAVAILABLE", 503,
                    "AI service unavailable");
        }
    }

    public AiTopicJson generateTopic(AiGenerateTopicRequest request) {
        try {
            AiTopicJson result = aiRestTemplate.postForObject(
                    "/internal/ai/generate-topic", request, AiTopicJson.class);
            if (result == null) {
                throw new ApiBusinessException("SERVICE_UNAVAILABLE", 503,
                        "AI service returned empty response");
            }
            return result;
        } catch (RestClientException e) {
            throw new ApiBusinessException("SERVICE_UNAVAILABLE", 503,
                    "AI service unavailable");
        }
    }

    public AiLessonJson regenerateLesson(AiRegenerateLessonRequest request) {
        try {
            AiLessonJson result = aiRestTemplate.postForObject(
                    "/internal/ai/regenerate-lesson", request, AiLessonJson.class);
            if (result == null) {
                throw new ApiBusinessException("SERVICE_UNAVAILABLE", 503,
                        "AI service returned empty response");
            }
            return result;
        } catch (RestClientException e) {
            throw new ApiBusinessException("SERVICE_UNAVAILABLE", 503,
                    "AI service unavailable");
        }
    }
}

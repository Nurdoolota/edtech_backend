package com.lms.learning.service.checker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.learning.dto.ai.AiEvaluateRequest;
import com.lms.learning.dto.ai.AiEvaluateResponse;
import com.lms.learning.entity.Task;
import com.lms.learning.entity.TaskType;
import com.lms.learning.exception.ApiBusinessException;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestBodySpec;
import org.springframework.web.client.RestClient.RequestBodyUriSpec;
import org.springframework.web.client.RestClient.ResponseSpec;
import org.springframework.web.client.RestClientException;

class AiBasedCheckerTest {

    @Test
    void check_textTask_returnsEvaluationResult() {
        AiEvaluateResponse aiResponse = new AiEvaluateResponse(
                BigDecimal.valueOf(85), "Good answer.", "Accepted");

        RestClient restClient = mock(RestClient.class);
        RequestBodyUriSpec uriSpec = mock(RequestBodyUriSpec.class);
        RequestBodySpec bodySpec = mock(RequestBodySpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.contentType(any(MediaType.class))).thenReturn(bodySpec);
        when(bodySpec.body(any(AiEvaluateRequest.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(eq(AiEvaluateResponse.class))).thenReturn(aiResponse);

        AiBasedChecker checker = new AiBasedChecker(restClient, new ObjectMapper());
        Task task = taskWith(TaskType.TEXT, Map.of("sourceText", "IoT article", "questions",
                java.util.List.of("What is IoT?"), "level", "B2"));

        EvaluationResult result = checker.check(task, "IoT is a network of devices.");

        assertThat(result.score()).isEqualByComparingTo(BigDecimal.valueOf(85));
        assertThat(result.feedback()).isEqualTo("Good answer.");
    }

    @Test
    void check_aiServiceDown_throwsServiceUnavailable() {
        RestClient restClient = mock(RestClient.class);
        RequestBodyUriSpec uriSpec = mock(RequestBodyUriSpec.class);
        RequestBodySpec bodySpec = mock(RequestBodySpec.class);

        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.contentType(any(MediaType.class))).thenReturn(bodySpec);
        when(bodySpec.body(any(AiEvaluateRequest.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenThrow(new RestClientException("connection refused"));

        AiBasedChecker checker = new AiBasedChecker(restClient, new ObjectMapper());
        Task task = taskWith(TaskType.TRANSLATION, Map.of(
                "sourceText", "Some text", "instructions", "Translate"));

        assertThatThrownBy(() -> checker.check(task, "Some translation"))
                .isInstanceOf(ApiBusinessException.class)
                .hasMessageContaining("temporarily unavailable");
    }

    @Test
    void check_readingComprehensionTask_usesCorrectPromptCode() {
        AiEvaluateResponse aiResponse = new AiEvaluateResponse(
                java.math.BigDecimal.valueOf(75), "Well explained.", "OK");

        RestClient restClient = mock(RestClient.class);
        RequestBodyUriSpec uriSpec = mock(RequestBodyUriSpec.class);
        RequestBodySpec bodySpec = mock(RequestBodySpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.contentType(any(MediaType.class))).thenReturn(bodySpec);
        when(bodySpec.body(any(AiEvaluateRequest.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(eq(AiEvaluateResponse.class))).thenReturn(aiResponse);

        AiBasedChecker checker = new AiBasedChecker(restClient, new ObjectMapper());
        Task task = taskWith(TaskType.READING_COMPREHENSION, Map.of(
                "text", "Some article", "questions", java.util.List.of("Q1?")));

        EvaluationResult result = checker.check(task, "My answer");
        assertThat(result.score()).isEqualByComparingTo(java.math.BigDecimal.valueOf(75));
    }

    @Test
    void check_imageDescriptionTask_usesCorrectPromptCode() {
        AiEvaluateResponse aiResponse = new AiEvaluateResponse(
                java.math.BigDecimal.valueOf(90), "Great description.", "OK");

        RestClient restClient = mock(RestClient.class);
        RequestBodyUriSpec uriSpec = mock(RequestBodyUriSpec.class);
        RequestBodySpec bodySpec = mock(RequestBodySpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.contentType(any(MediaType.class))).thenReturn(bodySpec);
        when(bodySpec.body(any(AiEvaluateRequest.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(eq(AiEvaluateResponse.class))).thenReturn(aiResponse);

        AiBasedChecker checker = new AiBasedChecker(restClient, new ObjectMapper());
        Task task = taskWith(TaskType.IMAGE_DESCRIPTION, Map.of(
                "imageUrl", "https://example.com/img.jpg"));

        EvaluationResult result = checker.check(task, "I see a cat.");
        assertThat(result.score()).isEqualByComparingTo(java.math.BigDecimal.valueOf(90));
    }

    private Task taskWith(TaskType type, Map<String, Object> content) {
        return new Task() {
            @Override
            public Long getId() { return 1L; }

            @Override
            public TaskType getType() { return type; }

            @Override
            public Map<String, Object> getContent() { return content; }
        };
    }
}

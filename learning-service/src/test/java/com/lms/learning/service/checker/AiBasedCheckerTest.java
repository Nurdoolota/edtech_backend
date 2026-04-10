package com.lms.learning.service.checker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lms.learning.dto.ai.AiEvaluateResponse;
import com.lms.learning.entity.Task;
import com.lms.learning.entity.TaskType;
import com.lms.learning.exception.ApiBusinessException;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestBodySpec;
import org.springframework.web.client.RestClient.RequestBodyUriSpec;
import org.springframework.web.client.RestClient.ResponseSpec;
import org.springframework.web.client.RestClientException;

class AiBasedCheckerTest {

    private RestClient restClient;
    private AiBasedChecker checker;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        restClient = mock(RestClient.class);
        checker = new AiBasedChecker(restClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void check_textTask_returnsEvaluationResult() {
        AiEvaluateResponse aiResponse = new AiEvaluateResponse(
                BigDecimal.valueOf(85), "Good answer.", "Accepted");

        RequestBodyUriSpec uriSpec = mock(RequestBodyUriSpec.class);
        RequestBodySpec bodySpec = mock(RequestBodySpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.contentType(any(MediaType.class))).thenReturn(bodySpec);
        when(bodySpec.body(any())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(AiEvaluateResponse.class)).thenReturn(aiResponse);

        Task task = taskWith(TaskType.TEXT, Map.of("sourceText", "IoT article", "questions",
                java.util.List.of("What is IoT?"), "level", "B2"));

        EvaluationResult result = checker.check(task, "IoT is a network of devices.");

        assertThat(result.score()).isEqualByComparingTo(BigDecimal.valueOf(85));
        assertThat(result.feedback()).isEqualTo("Good answer.");
    }

    @Test
    @SuppressWarnings("unchecked")
    void check_aiServiceDown_throwsServiceUnavailable() {
        RequestBodyUriSpec uriSpec = mock(RequestBodyUriSpec.class);
        RequestBodySpec bodySpec = mock(RequestBodySpec.class);

        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.contentType(any(MediaType.class))).thenReturn(bodySpec);
        when(bodySpec.body(any())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenThrow(new RestClientException("connection refused"));

        Task task = taskWith(TaskType.TRANSLATION, Map.of(
                "sourceText", "Some text", "instructions", "Translate"));

        assertThatThrownBy(() -> checker.check(task, "Some translation"))
                .isInstanceOf(ApiBusinessException.class)
                .hasMessageContaining("temporarily unavailable");
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

package com.lms.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.lms.ai.config.LlmProperties;
import com.lms.ai.dto.HealthResponse;
import com.lms.ai.exception.ApiBusinessException;
import com.lms.ai.llm.LlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiHealthServiceTest {

    @Mock
    private LlmClient llmClient;

    private AiHealthService healthService;
    private LlmProperties props;

    @BeforeEach
    void setUp() {
        props = new LlmProperties(
                "http://localhost:1234/v1",
                "test-model",
                "test-key",
                28);
        healthService = new AiHealthService(llmClient, props);
    }

    @Test
    void probe_llmReachable_returnsUp() {
        when(llmClient.complete(anyString(), anyDouble(), anyInt())).thenReturn("OK");

        HealthResponse response = healthService.probe();

        assertThat(response.ai()).isEqualTo("UP");
        assertThat(response.model()).isEqualTo("test-model");
        assertThat(response.latencyMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void probe_llmThrowsServiceUnavailable_returnsDown() {
        when(llmClient.complete(anyString(), anyDouble(), anyInt()))
                .thenThrow(ApiBusinessException.serviceUnavailable("LLM provider is unavailable."));

        HealthResponse response = healthService.probe();

        assertThat(response.ai()).isEqualTo("DOWN");
        assertThat(response.model()).isEqualTo("test-model");
        assertThat(response.latencyMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void probe_llmThrowsRuntimeException_returnsDown() {
        when(llmClient.complete(anyString(), anyDouble(), anyInt()))
                .thenThrow(new RuntimeException("Connection refused"));

        HealthResponse response = healthService.probe();

        assertThat(response.ai()).isEqualTo("DOWN");
        assertThat(response.model()).isEqualTo("test-model");
        assertThat(response.latencyMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void probe_latencyIsNonNegative() {
        when(llmClient.complete(anyString(), anyDouble(), anyInt())).thenReturn("OK");

        HealthResponse response = healthService.probe();

        assertThat(response.latencyMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void probe_modelMatchesConfiguration() {
        when(llmClient.complete(anyString(), anyDouble(), anyInt())).thenReturn("OK");

        HealthResponse response = healthService.probe();

        assertThat(response.model()).isEqualTo(props.model());
    }
}

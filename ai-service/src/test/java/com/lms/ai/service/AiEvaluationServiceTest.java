package com.lms.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.ai.dto.AiEvaluateRequest;
import com.lms.ai.dto.AiEvaluateRequest.AiOptions;
import com.lms.ai.dto.AiEvaluateResponse;
import com.lms.ai.exception.ApiBusinessException;
import com.lms.ai.llm.LlmClient;
import com.lms.ai.prompt.PromptTemplateService;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiEvaluationServiceTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private PromptTemplateService promptTemplateService;

    private AiEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new AiEvaluationService(llmClient, promptTemplateService, new ObjectMapper());
    }

    private AiEvaluateRequest buildRequest(String taskType) {
        return new AiEvaluateRequest(
                taskType,
                taskType.toLowerCase() + "_evaluation",
                Map.of("SOURCE_TEXT", "text"),
                "student answer",
                new AiOptions(0.2, 1024));
    }

    @Test
    void evaluate_acceptedVerdict_returnsScore1() {
        when(promptTemplateService.render(anyString(), any(), anyString()))
                .thenReturn("prompt");
        when(llmClient.complete(anyString(), anyDouble(), anyInt()))
                .thenReturn("""
                        {"verdict":"Accepted","feedback":"Good job.","corrections":[]}
                        """);

        AiEvaluateResponse response = service.evaluate(buildRequest("TEXT"));

        assertThat(response.score()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(response.verdict()).isEqualTo("Accepted");
        assertThat(response.feedback()).isEqualTo("Good job.");
    }

    @Test
    void evaluate_notAcceptedVerdict_returnsScore0() {
        when(promptTemplateService.render(anyString(), any(), anyString()))
                .thenReturn("prompt");
        when(llmClient.complete(anyString(), anyDouble(), anyInt()))
                .thenReturn("{\"verdict\":\"Not Accepted\",\"feedback\":\"Wrong answer.\",\"corrections\":[]}");

        AiEvaluateResponse response = service.evaluate(buildRequest("TRANSLATION"));

        assertThat(response.score()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.verdict()).isEqualTo("Not Accepted");
    }

    @Test
    void evaluate_debatesReturnsNumericScore() {
        when(promptTemplateService.render(anyString(), any(), anyString()))
                .thenReturn("prompt");
        when(llmClient.complete(anyString(), anyDouble(), anyInt()))
                .thenReturn("{\"score\":0.75,\"feedback\":\"Good debate.\",\"session_summary\":\"The student argued well.\"}");

        AiEvaluateResponse response = service.evaluate(buildRequest("DEBATES"));

        assertThat(response.score()).isEqualByComparingTo(new BigDecimal("0.75"));
        assertThat(response.verdict()).isNull();
        assertThat(response.feedback()).isEqualTo("Good debate.");
    }

    @Test
    void evaluate_markdownFencedResponse_parsedCorrectly() {
        when(promptTemplateService.render(anyString(), any(), anyString()))
                .thenReturn("prompt");
        when(llmClient.complete(anyString(), anyDouble(), anyInt()))
                .thenReturn("```json\n{\"verdict\":\"Accepted\",\"feedback\":\"Great.\"}\n```");

        AiEvaluateResponse response = service.evaluate(buildRequest("VIDEO"));

        assertThat(response.score()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(response.feedback()).isEqualTo("Great.");
    }

    @Test
    void evaluate_invalidJson_throwsBadGateway() {
        when(promptTemplateService.render(anyString(), any(), anyString()))
                .thenReturn("prompt");
        when(llmClient.complete(anyString(), anyDouble(), anyInt()))
                .thenReturn("This is not JSON at all.");

        assertThatThrownBy(() -> service.evaluate(buildRequest("TEXT")))
                .isInstanceOf(ApiBusinessException.class)
                .hasMessageContaining("invalid JSON");
    }

    @Test
    void stripMarkdownFences_cleanJson_unchanged() {
        String json = "{\"verdict\":\"Accepted\"}";
        assertThat(AiEvaluationService.stripMarkdownFences(json)).isEqualTo(json);
    }

    @Test
    void stripMarkdownFences_withJsonTag_stripped() {
        String fenced = "```json\n{\"verdict\":\"Accepted\"}\n```";
        assertThat(AiEvaluationService.stripMarkdownFences(fenced))
                .isEqualTo("{\"verdict\":\"Accepted\"}");
    }

    @Test
    void stripMarkdownFences_withoutTag_stripped() {
        String fenced = "```\n{\"score\":0.5}\n```";
        assertThat(AiEvaluationService.stripMarkdownFences(fenced))
                .isEqualTo("{\"score\":0.5}");
    }

    @Test
    void evaluate_debatesScoreClampedAbove1() {
        when(promptTemplateService.render(anyString(), any(), anyString()))
                .thenReturn("prompt");
        when(llmClient.complete(anyString(), anyDouble(), anyInt()))
                .thenReturn("{\"score\":1.5,\"feedback\":\"Excellent.\"}");

        AiEvaluateResponse response = service.evaluate(buildRequest("DEBATES"));

        assertThat(response.score()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void evaluate_debatesScoreClampedBelow0() {
        when(promptTemplateService.render(anyString(), any(), anyString()))
                .thenReturn("prompt");
        when(llmClient.complete(anyString(), anyDouble(), anyInt()))
                .thenReturn("{\"score\":-0.5,\"feedback\":\"Poor.\"}");

        AiEvaluateResponse response = service.evaluate(buildRequest("DEBATES"));

        assertThat(response.score()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}

package com.lms.ai.exception;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lms.ai.config.SecurityConfig;
import com.lms.ai.controller.AiEvaluateController;
import com.lms.ai.dto.AiEvaluateRequest;
import com.lms.ai.service.AiEvaluationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies that {@link GlobalExceptionHandler} maps {@link AiServiceException}
 * to HTTP 502 with the BAD_GATEWAY error envelope.
 */
@WebMvcTest(AiEvaluateController.class)
@Import(SecurityConfig.class)
class GlobalExceptionHandlerAiServiceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiEvaluationService evaluationService;

    private static final String EVALUATE_URL = "/internal/ai/evaluate";
    private static final String VALID_REQUEST_BODY = """
            {
              "task_type": "TEXT",
              "prompt_template_code": "text_evaluation",
              "task_content": {"SOURCE_TEXT": "hello"},
              "student_answer": "world"
            }
            """;

    @Test
    void evaluate_aiServiceExceptionThrown_returns502WithBadGatewayCode() throws Exception {
        when(evaluationService.evaluate(any(AiEvaluateRequest.class)))
                .thenThrow(new AiServiceException("LLM call failed after retries"));

        mockMvc.perform(post(EVALUATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("BAD_GATEWAY"))
                .andExpect(jsonPath("$.message").value("LLM call failed after retries"));
    }

    @Test
    void evaluate_aiServiceExceptionWithApiKey_apiKeyIsRedacted() throws Exception {
        when(evaluationService.evaluate(any(AiEvaluateRequest.class)))
                .thenThrow(new AiServiceException(
                        "Request failed with Bearer sk-secret123abc in header"));

        mockMvc.perform(post(EVALUATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("BAD_GATEWAY"))
                .andExpect(jsonPath("$.message").value("Request failed with Bearer [REDACTED] in header"));
    }

    @Test
    void evaluate_validRequest_returns200() throws Exception {
        com.lms.ai.dto.AiEvaluateResponse mockResponse =
                new com.lms.ai.dto.AiEvaluateResponse(
                        java.math.BigDecimal.ONE, "Good job.", "Accepted");
        when(evaluationService.evaluate(any(AiEvaluateRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post(EVALUATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(1))
                .andExpect(jsonPath("$.feedback").value("Good job."));
    }
}

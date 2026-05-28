package com.lms.ai.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lms.ai.config.SecurityConfig;
import com.lms.ai.dto.HealthResponse;
import com.lms.ai.service.AiHealthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AiHealthController.class)
@Import(SecurityConfig.class)
class AiHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiHealthService healthService;

    @Test
    void health_llmUp_returns200WithUpStatus() throws Exception {
        when(healthService.probe()).thenReturn(new HealthResponse("UP", "llama-3.1-8b-instant", 342));

        mockMvc.perform(get("/internal/ai/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ai").value("UP"))
                .andExpect(jsonPath("$.model").value("llama-3.1-8b-instant"))
                .andExpect(jsonPath("$.latencyMs").value(342));
    }

    @Test
    void health_llmDown_returns200WithDownStatus() throws Exception {
        when(healthService.probe()).thenReturn(new HealthResponse("DOWN", "local-model", 60015));

        mockMvc.perform(get("/internal/ai/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ai").value("DOWN"))
                .andExpect(jsonPath("$.model").value("local-model"))
                .andExpect(jsonPath("$.latencyMs").value(60015));
    }

    @Test
    void health_alwaysReturns200EvenWhenDown() throws Exception {
        when(healthService.probe()).thenReturn(new HealthResponse("DOWN", "local-model", 100));

        mockMvc.perform(get("/internal/ai/health"))
                .andExpect(status().isOk());
    }

    @Test
    void health_latencyMsIsNonNegative() throws Exception {
        when(healthService.probe()).thenReturn(new HealthResponse("UP", "local-model", 0));

        mockMvc.perform(get("/internal/ai/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latencyMs").value(0));
    }
}

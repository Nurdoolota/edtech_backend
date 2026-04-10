package com.lms.learning.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.learning.dto.PagedResponse;
import com.lms.learning.dto.SubmitAnswerRequest;
import com.lms.learning.dto.TaskResultResponse;
import com.lms.learning.dto.ValidateResultRequest;
import com.lms.learning.entity.ResultStatus;
import com.lms.learning.security.JwtUserPrincipal;
import com.lms.learning.security.RoleName;
import com.lms.learning.service.LearningService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LearningController.class)
class LearningControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean LearningService learningService;

    @Test
    @WithMockUser(roles = "STUDENT")
    void submit_asStudent_returns200() throws Exception {
        TaskResultResponse resp = sampleResponse();
        when(learningService.submitAnswer(eq(1L), any(), any(SubmitAnswerRequest.class)))
                .thenReturn(resp);

        mockMvc.perform(post("/api/v1/learning/tasks/1/submit")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SubmitAnswerRequest("[\"a\"]"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED"));
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void submit_asTeacher_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/learning/tasks/1/submit")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SubmitAnswerRequest("[\"a\"]"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void myResults_asStudent_returns200() throws Exception {
        when(learningService.getMyResults(any(), any(), any()))
                .thenReturn(new PagedResponse<>(List.of(sampleResponse()), 1L, 1));

        mockMvc.perform(get("/api/v1/learning/my-results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void taskResults_asTeacher_returns200() throws Exception {
        when(learningService.getTaskResults(eq(1L), any()))
                .thenReturn(new PagedResponse<>(List.of(sampleResponse()), 1L, 1));

        mockMvc.perform(get("/api/v1/learning/tasks/1/results"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void taskResults_asStudent_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/learning/tasks/1/results"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void validate_asTeacher_returns200() throws Exception {
        when(learningService.validateResult(eq(1L), any(ValidateResultRequest.class)))
                .thenReturn(sampleResponse(ResultStatus.VALIDATED_BY_TEACHER, BigDecimal.valueOf(95)));

        mockMvc.perform(patch("/api/v1/learning/results/1/validate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ValidateResultRequest(BigDecimal.valueOf(95), "Well done"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDATED_BY_TEACHER"));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void validate_asStudent_returns403() throws Exception {
        mockMvc.perform(patch("/api/v1/learning/results/1/validate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ValidateResultRequest(BigDecimal.valueOf(80), null))))
                .andExpect(status().isForbidden());
    }

    private TaskResultResponse sampleResponse() {
        return sampleResponse(ResultStatus.CHECKED, BigDecimal.valueOf(100));
    }

    private TaskResultResponse sampleResponse(ResultStatus status, BigDecimal score) {
        return new TaskResultResponse(1L, 1L, 10L, "[\"apple\"]", "Correct!", score,
                status, Instant.now(), Instant.now());
    }
}

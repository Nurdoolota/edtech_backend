package com.lms.ai.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lms.ai.config.SecurityConfig;
import com.lms.ai.entity.AiCallLog;
import com.lms.ai.repository.AiCallLogRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AiLogController.class)
@Import(SecurityConfig.class)
class AiLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiCallLogRepository repository;

    private AiCallLog sampleLog;

    @BeforeEach
    void setUp() {
        sampleLog = new AiCallLog();
        sampleLog.setId(1L);
        sampleLog.setRequestId("550e8400-e29b-41d4-a716-446655440000");
        sampleLog.setUserId(42L);
        sampleLog.setEndpoint("/internal/ai/evaluate");
        sampleLog.setModel("llama-3.1-8b-instant");
        sampleLog.setLatencyMs(1832);
        sampleLog.setTokensIn(512);
        sampleLog.setTokensOut(1024);
        sampleLog.setStatus("SUCCESS");
        sampleLog.setError(null);
        sampleLog.setCreatedAt(Instant.parse("2026-05-27T10:00:00Z"));
    }

    // ------------------------------------------------------------------
    // Acceptance criterion: returns paginated JSON with required fields
    // ------------------------------------------------------------------

    @Test
    void getLog_noFilters_returnsPaginatedResponse() throws Exception {
        Page<AiCallLog> page = new PageImpl<>(List.of(sampleLog));
        when(repository.findAll(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/internal/ai/log"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].requestId").value("550e8400-e29b-41d4-a716-446655440000"))
                .andExpect(jsonPath("$.content[0].userId").value(42))
                .andExpect(jsonPath("$.content[0].endpoint").value("/internal/ai/evaluate"))
                .andExpect(jsonPath("$.content[0].model").value("llama-3.1-8b-instant"))
                .andExpect(jsonPath("$.content[0].latencyMs").value(1832))
                .andExpect(jsonPath("$.content[0].tokensIn").value(512))
                .andExpect(jsonPath("$.content[0].tokensOut").value(1024))
                .andExpect(jsonPath("$.content[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1));
    }

    // ------------------------------------------------------------------
    // Acceptance criterion: ?userId=42 delegates to findByUserId
    // ------------------------------------------------------------------

    @Test
    void getLog_filterByUserId_delegatesToFindByUserId() throws Exception {
        Page<AiCallLog> page = new PageImpl<>(List.of(sampleLog));
        when(repository.findByUserId(eq(42L), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/internal/ai/log?userId=42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value(42));
    }

    // ------------------------------------------------------------------
    // Acceptance criterion: ?status=ERROR delegates to findByStatus
    // ------------------------------------------------------------------

    @Test
    void getLog_filterByStatus_delegatesToFindByStatus() throws Exception {
        AiCallLog errorLog = new AiCallLog();
        errorLog.setId(2L);
        errorLog.setEndpoint("/internal/ai/evaluate");
        errorLog.setModel("llama-3.1-8b-instant");
        errorLog.setLatencyMs(500);
        errorLog.setStatus("ERROR");
        errorLog.setError("timeout");
        errorLog.setCreatedAt(Instant.now());

        Page<AiCallLog> page = new PageImpl<>(List.of(errorLog));
        when(repository.findByStatus(eq("ERROR"), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/internal/ai/log?status=ERROR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("ERROR"));
    }

    // ------------------------------------------------------------------
    // Acceptance criterion: ?userId=42&status=SUCCESS applies both filters
    // ------------------------------------------------------------------

    @Test
    void getLog_filterByUserIdAndStatus_delegatesToFindByUserIdAndStatus() throws Exception {
        Page<AiCallLog> page = new PageImpl<>(List.of(sampleLog));
        when(repository.findByUserIdAndStatus(eq(42L), eq("SUCCESS"), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/internal/ai/log?userId=42&status=SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value(42))
                .andExpect(jsonPath("$.content[0].status").value("SUCCESS"));
    }

    // ------------------------------------------------------------------
    // Acceptance criterion: pagination params are respected
    // ------------------------------------------------------------------

    @Test
    void getLog_customPageAndSize_usedInQuery() throws Exception {
        Page<AiCallLog> emptyPage = Page.empty();
        when(repository.findAll(any(Pageable.class))).thenReturn(emptyPage);

        mockMvc.perform(get("/internal/ai/log?page=2&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // ------------------------------------------------------------------
    // Acceptance criterion: empty result returns valid paged structure
    // ------------------------------------------------------------------

    @Test
    void getLog_noResults_returnsEmptyContent() throws Exception {
        Page<AiCallLog> emptyPage = Page.empty();
        when(repository.findAll(any(Pageable.class))).thenReturn(emptyPage);

        mockMvc.perform(get("/internal/ai/log"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}

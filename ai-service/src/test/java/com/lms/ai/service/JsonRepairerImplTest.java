package com.lms.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.ai.exception.JsonRepairException;
import com.lms.ai.llm.LlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JsonRepairerImplTest {

    @Mock
    private LlmClient llmClient;

    private JsonRepairerImpl repairer;

    @BeforeEach
    void setUp() {
        repairer = new JsonRepairerImpl(llmClient, new ObjectMapper());
    }

    @Test
    void repair_llmReturnsParseable_returnsRepairedJson() throws JsonRepairException {
        String brokenJson = "{value: no-quotes}";
        String repairedJson = "{\"value\":\"no-quotes\"}";
        when(llmClient.complete(contains(brokenJson), anyDouble(), anyInt()))
                .thenReturn(repairedJson);

        String result = repairer.repair(brokenJson, "");

        assertThat(result).isEqualTo(repairedJson);
        // Verify the repair prompt contained the broken JSON
        verify(llmClient).complete(contains(brokenJson), anyDouble(), anyInt());
    }

    @Test
    void repair_llmReturnsStillInvalidJson_throwsJsonRepairException() {
        String brokenJson = "{bad json";
        when(llmClient.complete(contains(brokenJson), anyDouble(), anyInt()))
                .thenReturn("still not valid {json");

        assertThatThrownBy(() -> repairer.repair(brokenJson, ""))
                .isInstanceOf(JsonRepairException.class)
                .hasMessageContaining("still invalid");
    }

    @Test
    void repair_llmCallFails_throwsJsonRepairException() {
        String brokenJson = "{broken}";
        when(llmClient.complete(contains(brokenJson), anyDouble(), anyInt()))
                .thenThrow(new RuntimeException("LLM unavailable"));

        assertThatThrownBy(() -> repairer.repair(brokenJson, "anything"))
                .isInstanceOf(JsonRepairException.class)
                .hasMessageContaining("LLM call for JSON repair failed");
    }

    @Test
    void repair_promptContainsRepairInstruction() throws JsonRepairException {
        String brokenJson = "{\"x\":}";
        String validJson = "{\"x\":null}";
        when(llmClient.complete(contains("Fix the following JSON"), anyDouble(), anyInt()))
                .thenReturn(validJson);

        repairer.repair(brokenJson, "optional hint");

        verify(llmClient).complete(
                contains("Fix the following JSON so it is valid. Return only the corrected JSON with no extra text:"),
                anyDouble(), anyInt());
    }
}

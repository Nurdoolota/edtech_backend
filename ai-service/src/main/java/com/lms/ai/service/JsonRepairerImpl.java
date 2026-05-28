package com.lms.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.ai.exception.JsonRepairException;
import com.lms.ai.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Sends a single repair prompt to the LLM and validates the response is parseable JSON.
 * Maximum one repair attempt — no internal retry loop.
 */
@Component
public class JsonRepairerImpl implements JsonRepairer {

    private static final Logger log = LoggerFactory.getLogger(JsonRepairerImpl.class);

    private static final String REPAIR_INSTRUCTION =
            "Fix the following JSON so it is valid. Return only the corrected JSON with no extra text:\n";

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public JsonRepairerImpl(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String repair(String brokenJson, String hint) throws JsonRepairException {
        log.warn("Attempting JSON repair via LLM. Broken JSON length={}", brokenJson.length());

        String repairPrompt = REPAIR_INSTRUCTION + brokenJson;
        String repaired;
        try {
            repaired = llmClient.complete(repairPrompt, 0.0, 2048);
        } catch (Exception ex) {
            throw new JsonRepairException("LLM call for JSON repair failed: " + ex.getMessage(), ex);
        }

        // Validate the repaired JSON is parseable
        try {
            objectMapper.readTree(repaired);
        } catch (Exception parseEx) {
            log.error("JSON repair attempt still produced invalid JSON. Repaired output: {}", repaired);
            throw new JsonRepairException(
                    "Repaired JSON is still invalid: " + parseEx.getMessage(), parseEx);
        }

        log.info("JSON repair succeeded.");
        return repaired;
    }
}

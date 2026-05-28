package com.lms.ai.service;

import com.lms.ai.exception.JsonRepairException;

/**
 * Attempts to repair a broken JSON string by asking the LLM to fix it.
 */
public interface JsonRepairer {

    /**
     * Ask the LLM to repair {@code brokenJson} into valid JSON.
     *
     * @param brokenJson the raw, possibly-invalid JSON string returned by the LLM
     * @param hint       optional context hint (may be empty string)
     * @return a corrected JSON string (validated to be parseable)
     * @throws JsonRepairException if the LLM repair response is also invalid JSON
     */
    String repair(String brokenJson, String hint) throws JsonRepairException;
}

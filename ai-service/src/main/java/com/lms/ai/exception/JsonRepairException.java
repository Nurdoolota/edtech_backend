package com.lms.ai.exception;

/**
 * Thrown by {@link com.lms.ai.service.JsonRepairer} when the LLM's repair attempt still
 * produces invalid JSON. This is propagated immediately as non-retryable.
 */
public class JsonRepairException extends RuntimeException {

    public JsonRepairException(String message) {
        super(message);
    }

    public JsonRepairException(String message, Throwable cause) {
        super(message, cause);
    }
}

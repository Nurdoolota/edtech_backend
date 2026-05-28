package com.lms.ai.exception;

/**
 * Thrown when the LLM call fails after all retry attempts or when JSON repair cannot produce
 * valid JSON. Maps to HTTP 502 BAD_GATEWAY.
 */
public class AiServiceException extends RuntimeException {

    public AiServiceException(String message) {
        super(message);
    }

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}

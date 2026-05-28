package com.lms.ai.exception;

import com.lms.ai.dto.ApiError;
import com.lms.ai.service.AiRetryExecutor;
import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiBusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(ApiBusinessException ex) {
        log.warn("Business error [{}]: {}", ex.getCode(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus())
                .body(new ApiError(ex.getCode(), ex.getMessage(), MDC.get("requestId")));
    }

    /**
     * Maps {@link AiServiceException} (LLM call exhausted / JSON repair failed) to HTTP 502.
     * API keys are redacted from logged messages to prevent secret leakage.
     */
    @ExceptionHandler(AiServiceException.class)
    public ResponseEntity<ApiError> handleAiService(AiServiceException ex) {
        String safeMessage = AiRetryExecutor.redactApiKey(ex.getMessage());
        log.error("AI service error [BAD_GATEWAY]: {}", safeMessage);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiError("BAD_GATEWAY",
                        safeMessage != null ? safeMessage : "LLM returned unparseable JSON after retries",
                        MDC.get("requestId")));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("VALIDATION_ERROR", details, MDC.get("requestId")));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        String safeMessage = AiRetryExecutor.redactApiKey(ex.getMessage());
        log.error("Unexpected error on {}: {}", request.getRequestURI(), safeMessage, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "An unexpected error occurred.", MDC.get("requestId")));
    }
}

package com.lms.learning.exception;

import com.lms.learning.config.RequestIdFilter;
import com.lms.learning.dto.ApiError;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.lms.learning.exception.TaskLockedException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TaskLockedException.class)
    public ResponseEntity<ApiError> taskLocked(TaskLockedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("TASK_LOCKED", ex.getMessage(), rid()));
    }

    @ExceptionHandler(ApiBusinessException.class)
    public ResponseEntity<ApiError> business(ApiBusinessException ex) {
        return ResponseEntity.status(ex.getHttpStatus())
                .body(new ApiError(ex.getCode(), ex.getMessage(), rid()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", msg, rid()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> constraint(ConstraintViolationException ex) {
        return ResponseEntity.badRequest()
                .body(new ApiError("VALIDATION_ERROR", ex.getMessage(), rid()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> fallback(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "Unexpected error", rid()));
    }

    private static String rid() {
        return MDC.get(RequestIdFilter.MDC_KEY);
    }
}

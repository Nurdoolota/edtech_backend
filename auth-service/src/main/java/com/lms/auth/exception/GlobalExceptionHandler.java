package com.lms.auth.exception;

import com.lms.auth.config.RequestIdFilter;
import com.lms.auth.dto.ApiError;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiBusinessException.class)
    public ResponseEntity<ApiError> business(ApiBusinessException ex) {
        return ResponseEntity.status(ex.getHttpStatus())
                .body(new ApiError(ex.getCode(), ex.getMessage(), rid()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
        String msg =
                ex.getBindingResult().getFieldErrors().stream()
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

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> responseStatus(ResponseStatusException ex) {
        String code = ex.getStatusCode().value() == 429
                ? "TOO_MANY_REQUESTS"
                : ex.getStatusCode().value() == 400
                ? "BAD_REQUEST"
                : "ERROR";
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ApiError(code, ex.getReason(), rid()));
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

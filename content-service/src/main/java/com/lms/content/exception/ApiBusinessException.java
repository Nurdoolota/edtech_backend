package com.lms.content.exception;

public class ApiBusinessException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    public ApiBusinessException(String code, int httpStatus, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public static ApiBusinessException notFound(String entity, Long id) {
        return new ApiBusinessException("NOT_FOUND", 404, entity + " not found: " + id);
    }

    public static ApiBusinessException forbidden() {
        return new ApiBusinessException("FORBIDDEN", 403, "Access denied");
    }

    public static ApiBusinessException conflict(String message) {
        return new ApiBusinessException("CONFLICT", 409, message);
    }

    public static ApiBusinessException badRequest(String message) {
        return new ApiBusinessException("VALIDATION_ERROR", 400, message);
    }

    public String getCode() { return code; }

    public int getHttpStatus() { return httpStatus; }
}

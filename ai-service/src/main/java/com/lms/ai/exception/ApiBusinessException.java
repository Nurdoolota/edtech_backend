package com.lms.ai.exception;

import org.springframework.http.HttpStatus;

public class ApiBusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private ApiBusinessException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiBusinessException badRequest(String message) {
        return new ApiBusinessException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static ApiBusinessException serviceUnavailable(String message) {
        return new ApiBusinessException(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", message);
    }

    public static ApiBusinessException badGateway(String message) {
        return new ApiBusinessException(HttpStatus.BAD_GATEWAY, "BAD_GATEWAY", message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}

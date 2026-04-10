package com.lms.gateway.exception;

import com.lms.gateway.filter.CorrelationIdFilter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Catches unhandled exceptions from Spring Cloud Gateway itself (e.g. no route found,
 * downstream connection errors) and returns a uniform {code, message, requestId} JSON.
 *
 * Order(-1) runs before Spring Boot's DefaultErrorWebExceptionHandler (order 1).
 */
@Component
@Order(-1)
public class GatewayErrorHandler implements ErrorWebExceptionHandler {

    private static final Map<HttpStatus, String> STATUS_TO_CODE = Map.of(
            HttpStatus.NOT_FOUND,               "NOT_FOUND",
            HttpStatus.UNAUTHORIZED,            "UNAUTHORIZED",
            HttpStatus.FORBIDDEN,               "FORBIDDEN",
            HttpStatus.BAD_REQUEST,             "BAD_REQUEST",
            HttpStatus.TOO_MANY_REQUESTS,       "RATE_LIMIT_EXCEEDED",
            HttpStatus.SERVICE_UNAVAILABLE,     "SERVICE_UNAVAILABLE",
            HttpStatus.GATEWAY_TIMEOUT,         "GATEWAY_TIMEOUT",
            HttpStatus.BAD_GATEWAY,             "BAD_GATEWAY"
    );

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status = resolveStatus(ex);
        String code = STATUS_TO_CODE.getOrDefault(status, "INTERNAL_ERROR");
        String message = userMessage(ex, status);
        String requestId = exchange.getRequest().getHeaders()
                .getFirst(CorrelationIdFilter.REQUEST_ID_HEADER);

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"code\":\"%s\",\"message\":\"%s\",\"requestId\":\"%s\"}",
                code, escape(message), requestId != null ? requestId : "");

        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private HttpStatus resolveStatus(Throwable ex) {
        if (ex instanceof ResponseStatusException rse) {
            return HttpStatus.resolve(rse.getStatusCode().value());
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String userMessage(Throwable ex, HttpStatus status) {
        if (status == HttpStatus.NOT_FOUND) {
            return "Route not found";
        }
        if (status == HttpStatus.SERVICE_UNAVAILABLE || status == HttpStatus.BAD_GATEWAY
                || status == HttpStatus.GATEWAY_TIMEOUT) {
            return "Downstream service is temporarily unavailable";
        }
        if (ex instanceof ResponseStatusException rse && rse.getReason() != null) {
            return rse.getReason();
        }
        return "An unexpected error occurred";
    }

    /** Escapes double-quotes and backslashes so the message is safe inside a JSON string. */
    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

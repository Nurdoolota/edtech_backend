package com.lms.gateway.filter;

import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Ensures every request carries an X-Request-Id header (generates UUID if absent).
 * Forwards the same ID in the downstream request and adds it to the response.
 * Must run first (order = -3) so subsequent filters and downstream logs can use it.
 */
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    public int getOrder() {
        return -3;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestId = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        final String finalRequestId = requestId;
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(REQUEST_ID_HEADER, finalRequestId)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        return chain.filter(mutatedExchange)
                .then(Mono.fromRunnable(() -> {
                    // #region agent log
                    boolean committed = mutatedExchange.getResponse().isCommitted();
                    dbgLog("CorrelationIdFilter.java:44", "then() callback fired", "H-A",
                            "{\"committed\":" + committed + ",\"requestId\":\"" + finalRequestId + "\"}");
                    // #endregion
                    try {
                        mutatedExchange.getResponse().getHeaders()
                                .set(REQUEST_ID_HEADER, finalRequestId);
                        // #region agent log
                        dbgLog("CorrelationIdFilter.java:50", "headers.set succeeded", "H-A", "{\"success\":true}");
                        // #endregion
                    } catch (Exception e) {
                        // #region agent log
                        dbgLog("CorrelationIdFilter.java:53", "headers.set threw: " + e.getClass().getSimpleName(), "H-A",
                                "{\"exception\":\"" + e.getMessage() + "\"}");
                        // #endregion
                    }
                }));
    }

    // #region agent log
    private static void dbgLog(String location, String message, String hypothesisId, String dataJson) {
        String line = String.format(
                "{\"sessionId\":\"5bf165\",\"hypothesisId\":\"%s\",\"location\":\"%s\",\"message\":\"%s\",\"data\":%s,\"timestamp\":%d}%n",
                hypothesisId, location, message, dataJson, Instant.now().toEpochMilli());
        try (FileWriter fw = new FileWriter("debug-5bf165.log", true)) { fw.write(line); }
        catch (IOException ignored) {}
    }
    // #endregion
}

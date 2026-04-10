package com.lms.gateway.filter;

import com.lms.gateway.config.RateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Per-IP rate limiter using Bucket4j in-memory buckets (no Redis required).
 * Returns 429 with uniform error JSON when the bucket is exhausted.
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int requestsPerSecond;

    public RateLimitFilter(RateLimitProperties properties) {
        this.requestsPerSecond = properties.getRequestsPerSecond();
    }

    @Override
    public int getOrder() {
        return -2;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String ip = resolveIp(exchange);
        Bucket bucket = buckets.computeIfAbsent(ip, k -> buildBucket());

        if (bucket.tryConsume(1)) {
            return chain.filter(exchange);
        }

        String requestId = exchange.getRequest().getHeaders().getFirst(CorrelationIdFilter.REQUEST_ID_HEADER);
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
        String body = String.format(
                "{\"code\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"Too many requests\",\"requestId\":\"%s\"}",
                requestId != null ? requestId : "");
        var buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes());
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private Bucket buildBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(requestsPerSecond)
                        .refillGreedy(requestsPerSecond, Duration.ofSeconds(1))
                        .build())
                .build();
    }

    private String resolveIp(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote != null) {
            return remote.getAddress().getHostAddress();
        }
        return "unknown";
    }
}

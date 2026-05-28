package com.lms.gateway.filter;

import com.lms.gateway.config.RateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Per-IP global rate limiter plus tighter per-IP/per-user hourly caps for
 * sensitive endpoints. Runs after JwtAuthFilter (order -1) so X-User-Id is
 * already injected when per-user checks execute.
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final ConcurrentHashMap<String, Bucket> globalBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> fpBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> importBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> aiGenerateBuckets = new ConcurrentHashMap<>();

    private final RateLimitProperties props;

    public RateLimitFilter(RateLimitProperties properties) {
        this.props = properties;
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();

        if (HttpMethod.POST.equals(method)) {
            if ("/api/v1/auth/forgot-password".equals(path)) {
                String ip = getRemoteIp(exchange);
                Bucket b = fpBuckets.computeIfAbsent(ip, k -> buildHourlyBucket(props.getForgotPasswordPerIpHour()));
                if (!b.tryConsume(1)) return rateLimitResponse(exchange);

            } else if ("/api/v1/content/courses/import".equals(path)) {
                String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
                if (userId != null) {
                    Bucket b = importBuckets.computeIfAbsent(userId, k -> buildHourlyBucket(props.getImportPerUserHour()));
                    if (!b.tryConsume(1)) return rateLimitResponse(exchange);
                }

            } else if (path.startsWith("/api/v1/content/ai/generate-")) {
                String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
                if (userId != null) {
                    Bucket b = aiGenerateBuckets.computeIfAbsent(userId, k -> buildHourlyBucket(props.getAiGeneratePerUserHour()));
                    if (!b.tryConsume(1)) return rateLimitResponse(exchange);
                }
            }
        }

        String ip = getRemoteIp(exchange);
        Bucket globalBucket = globalBuckets.computeIfAbsent(ip, k -> buildSecondBucket(props.getRequestsPerSecond()));
        if (!globalBucket.tryConsume(1)) return rateLimitResponse(exchange);

        return chain.filter(exchange);
    }

    private static Bucket buildHourlyBucket(int capacity) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillIntervally(capacity, Duration.ofHours(1))
                        .build())
                .build();
    }

    private static Bucket buildSecondBucket(int rps) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(rps)
                        .refillGreedy(rps, Duration.ofSeconds(1))
                        .build())
                .build();
    }

    private Mono<Void> rateLimitResponse(ServerWebExchange exchange) {
        ServerHttpResponse resp = exchange.getResponse();
        resp.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        resp.getHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        resp.getHeaders().set("Retry-After", "3600");
        String requestId = exchange.getRequest().getHeaders().getFirst(CorrelationIdFilter.REQUEST_ID_HEADER);
        String body = String.format(
                "{\"code\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"Rate limit exceeded. Please try again later.\",\"requestId\":\"%s\"}",
                requestId != null ? requestId : "");
        DataBuffer buf = resp.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return resp.writeWith(Mono.just(buf));
    }

    private String getRemoteIp(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote != null) {
            return remote.getAddress().getHostAddress();
        }
        return "unknown";
    }
}

package com.lms.gateway.filter;

import com.lms.gateway.security.JwtVerifier;
import com.lms.gateway.security.JwtVerifier.JwtClaims;
import io.jsonwebtoken.JwtException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Validates JWT on every non-public request.
 * On success: injects X-User-Id and X-User-Role headers for downstream services.
 * On failure or missing token: returns 401 with uniform error JSON.
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private final JwtVerifier jwtVerifier;

    public JwtAuthFilter(JwtVerifier jwtVerifier) {
        this.jwtVerifier = jwtVerifier;
    }

    @Override
    public int getOrder() {
        return -1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        if (isPublicPath(request)) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return writeUnauthorized(exchange, "Missing or malformed Authorization header");
        }

        String token = authHeader.substring(7);
        JwtClaims claims;
        try {
            claims = jwtVerifier.parseAccess(token);
        } catch (JwtException ex) {
            return writeUnauthorized(exchange, "Invalid or expired token");
        }

        ServerHttpRequest mutatedRequest = request.mutate()
                .header("X-User-Id", String.valueOf(claims.userId()))
                .header("X-User-Role", claims.role())
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private boolean isPublicPath(ServerHttpRequest request) {
        String path = request.getPath().value();
        HttpMethod method = request.getMethod();

        if (path.startsWith("/actuator")) {
            return true;
        }
        if (path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui") || path.startsWith("/webjars")) {
            return true;
        }
        if (HttpMethod.POST.equals(method)) {
            return "/api/v1/auth/register".equals(path)
                    || "/api/v1/auth/login".equals(path)
                    || "/api/v1/auth/refresh".equals(path);
        }
        return false;
    }

    private Mono<Void> writeUnauthorized(ServerWebExchange exchange, String message) {
        String requestId = exchange.getRequest().getHeaders().getFirst(CorrelationIdFilter.REQUEST_ID_HEADER);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
        String body = String.format(
                "{\"code\":\"UNAUTHORIZED\",\"message\":\"%s\",\"requestId\":\"%s\"}",
                message, requestId != null ? requestId : "");
        var buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes());
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}

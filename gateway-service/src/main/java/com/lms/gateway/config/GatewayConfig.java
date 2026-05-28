package com.lms.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Value("${ai.service.url:http://localhost:8084}")
    private String aiServiceUrl;

    @Value("${auth.service.url:http://localhost:8081}")
    private String authServiceUrl;

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("ai-health", r -> r
                        .path("/api/v1/ai/health")
                        .filters(f -> f.rewritePath("/api/v1/ai/health", "/internal/ai/health"))
                        .uri(aiServiceUrl))
                .route("avatar-static", r -> r
                        .path("/static/avatars/**")
                        .uri(authServiceUrl))
                .build();
    }
}

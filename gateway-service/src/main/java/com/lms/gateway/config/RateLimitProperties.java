package com.lms.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    private int requestsPerSecond = 20;

    public int getRequestsPerSecond() {
        return requestsPerSecond;
    }

    public void setRequestsPerSecond(int requestsPerSecond) {
        this.requestsPerSecond = requestsPerSecond;
    }
}

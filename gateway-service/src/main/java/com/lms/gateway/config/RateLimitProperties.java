package com.lms.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    private int requestsPerSecond = 20;
    private int forgotPasswordPerIpHour = 3;
    private int importPerUserHour = 5;
    private int aiGeneratePerUserHour = 20;

    public int getRequestsPerSecond() { return requestsPerSecond; }
    public void setRequestsPerSecond(int requestsPerSecond) { this.requestsPerSecond = requestsPerSecond; }

    public int getForgotPasswordPerIpHour() { return forgotPasswordPerIpHour; }
    public void setForgotPasswordPerIpHour(int v) { this.forgotPasswordPerIpHour = v; }

    public int getImportPerUserHour() { return importPerUserHour; }
    public void setImportPerUserHour(int v) { this.importPerUserHour = v; }

    public int getAiGeneratePerUserHour() { return aiGeneratePerUserHour; }
    public void setAiGeneratePerUserHour(int v) { this.aiGeneratePerUserHour = v; }
}

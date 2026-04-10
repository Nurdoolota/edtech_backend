package com.lms.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret;
    private long accessExpirySeconds = 900;
    private long refreshExpirySeconds = 604800;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getAccessExpirySeconds() {
        return accessExpirySeconds;
    }

    public void setAccessExpirySeconds(long accessExpirySeconds) {
        this.accessExpirySeconds = accessExpirySeconds;
    }

    public long getRefreshExpirySeconds() {
        return refreshExpirySeconds;
    }

    public void setRefreshExpirySeconds(long refreshExpirySeconds) {
        this.refreshExpirySeconds = refreshExpirySeconds;
    }
}

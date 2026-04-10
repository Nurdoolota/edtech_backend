package com.lms.learning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "content-service")
public class ContentServiceProperties {

    private String url;

    public String getUrl() { return url; }

    public void setUrl(String url) { this.url = url; }
}

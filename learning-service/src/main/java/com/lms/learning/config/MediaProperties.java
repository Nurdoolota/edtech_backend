package com.lms.learning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "learning.media")
public class MediaProperties {

    private String storagePath = "./data/student-media";
    private long maxSizeBytes = 20 * 1024 * 1024L;

    public String getStoragePath() { return storagePath; }

    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }

    public long getMaxSizeBytes() { return maxSizeBytes; }

    public void setMaxSizeBytes(long maxSizeBytes) { this.maxSizeBytes = maxSizeBytes; }
}

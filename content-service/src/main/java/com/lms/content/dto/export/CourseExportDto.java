package com.lms.content.dto.export;

import java.util.List;

public class CourseExportDto {
    private Long externalId;
    private String title;
    private String description;
    private String level;
    private String publishMode;
    private String accessStatus;
    private List<TopicExportDto> topics;

    public Long getExternalId() { return externalId; }
    public void setExternalId(Long externalId) { this.externalId = externalId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getPublishMode() { return publishMode; }
    public void setPublishMode(String publishMode) { this.publishMode = publishMode; }

    public String getAccessStatus() { return accessStatus; }
    public void setAccessStatus(String accessStatus) { this.accessStatus = accessStatus; }

    public List<TopicExportDto> getTopics() { return topics; }
    public void setTopics(List<TopicExportDto> topics) { this.topics = topics; }
}

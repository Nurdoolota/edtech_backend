package com.lms.content.dto.export;

import java.util.List;

public class TopicExportDto {
    private Long externalId;
    private String title;
    private String description;
    private int orderIndex;
    private List<LessonExportDto> lessons;

    public Long getExternalId() { return externalId; }
    public void setExternalId(Long externalId) { this.externalId = externalId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getOrderIndex() { return orderIndex; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }

    public List<LessonExportDto> getLessons() { return lessons; }
    public void setLessons(List<LessonExportDto> lessons) { this.lessons = lessons; }
}

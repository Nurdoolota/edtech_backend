package com.lms.content.dto.export;

import java.util.Map;

public class TaskExportDto {
    private Long externalId;
    private String type;
    private String title;
    private Map<String, Object> content;
    private int orderIndex;
    private String status;
    private String unlockMode;
    private Long prerequisiteExternalId;
    private Integer requiredScore;

    public Long getExternalId() { return externalId; }
    public void setExternalId(Long externalId) { this.externalId = externalId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Map<String, Object> getContent() { return content; }
    public void setContent(Map<String, Object> content) { this.content = content; }

    public int getOrderIndex() { return orderIndex; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getUnlockMode() { return unlockMode; }
    public void setUnlockMode(String unlockMode) { this.unlockMode = unlockMode; }

    public Long getPrerequisiteExternalId() { return prerequisiteExternalId; }
    public void setPrerequisiteExternalId(Long prerequisiteExternalId) {
        this.prerequisiteExternalId = prerequisiteExternalId;
    }

    public Integer getRequiredScore() { return requiredScore; }
    public void setRequiredScore(Integer requiredScore) { this.requiredScore = requiredScore; }
}

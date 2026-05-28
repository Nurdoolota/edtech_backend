package com.lms.content.dto.export;

public class BlockExportDto {
    private Long externalId;
    private String type;
    private String contentJson;
    private int orderIndex;
    private boolean aiGenerated;

    public Long getExternalId() { return externalId; }
    public void setExternalId(Long externalId) { this.externalId = externalId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getContentJson() { return contentJson; }
    public void setContentJson(String contentJson) { this.contentJson = contentJson; }

    public int getOrderIndex() { return orderIndex; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }

    public boolean isAiGenerated() { return aiGenerated; }
    public void setAiGenerated(boolean aiGenerated) { this.aiGenerated = aiGenerated; }
}

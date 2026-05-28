package com.lms.content.dto.export;

import java.util.List;

public class LessonExportDto {
    private Long externalId;
    private String title;
    private String status;
    private String unlockMode;
    private boolean visible;
    private int orderIndex;
    private List<BlockExportDto> blocks;
    private List<TaskExportDto> tasks;

    public Long getExternalId() { return externalId; }
    public void setExternalId(Long externalId) { this.externalId = externalId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getUnlockMode() { return unlockMode; }
    public void setUnlockMode(String unlockMode) { this.unlockMode = unlockMode; }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }

    public int getOrderIndex() { return orderIndex; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }

    public List<BlockExportDto> getBlocks() { return blocks; }
    public void setBlocks(List<BlockExportDto> blocks) { this.blocks = blocks; }

    public List<TaskExportDto> getTasks() { return tasks; }
    public void setTasks(List<TaskExportDto> tasks) { this.tasks = tasks; }
}

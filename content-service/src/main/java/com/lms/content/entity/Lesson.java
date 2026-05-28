package com.lms.content.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "lessons")
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "topic_id")
    private Long topicId;

    @Column(name = "order_index", nullable = false)
    private int orderIndex = 0;

    @Column(name = "global_order")
    private Integer globalOrder;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, length = 32)
    private String status = "DRAFT";

    @Column(name = "publish_mode", length = 16)
    private String publishMode;

    @Column(name = "unlock_mode", nullable = false, length = 16)
    private String unlockMode = "FREE";

    @Column(nullable = false)
    private boolean visible = true;

    @Column(name = "generation_metadata", columnDefinition = "jsonb")
    private String generationMetadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "published_at")
    private Instant publishedAt;

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public Long getCourseId() { return courseId; }

    public void setCourseId(Long courseId) { this.courseId = courseId; }

    public Long getTopicId() { return topicId; }

    public void setTopicId(Long topicId) { this.topicId = topicId; }

    public int getOrderIndex() { return orderIndex; }

    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }

    public Integer getGlobalOrder() { return globalOrder; }

    public void setGlobalOrder(Integer globalOrder) { this.globalOrder = globalOrder; }

    public String getTitle() { return title; }

    public void setTitle(String title) { this.title = title; }

    public String getStatus() { return status; }

    public void setStatus(String status) { this.status = status; }

    public String getPublishMode() { return publishMode; }

    public void setPublishMode(String publishMode) { this.publishMode = publishMode; }

    public String getUnlockMode() { return unlockMode; }

    public void setUnlockMode(String unlockMode) { this.unlockMode = unlockMode; }

    public boolean isVisible() { return visible; }

    public void setVisible(boolean visible) { this.visible = visible; }

    public String getGenerationMetadata() { return generationMetadata; }

    public void setGenerationMetadata(String generationMetadata) { this.generationMetadata = generationMetadata; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getPublishedAt() { return publishedAt; }

    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
}

package com.lms.content.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "lesson_blocks")
public class LessonBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lesson_id", nullable = false)
    private Long lessonId;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(nullable = false, length = 16)
    private String type;

    @Column(name = "content_json", nullable = false, columnDefinition = "jsonb")
    private String contentJson;

    @Column(name = "ai_generated", nullable = false)
    private boolean aiGenerated = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public Long getLessonId() { return lessonId; }

    public void setLessonId(Long lessonId) { this.lessonId = lessonId; }

    public int getOrderIndex() { return orderIndex; }

    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }

    public String getType() { return type; }

    public void setType(String type) { this.type = type; }

    public String getContentJson() { return contentJson; }

    public void setContentJson(String contentJson) { this.contentJson = contentJson; }

    public boolean isAiGenerated() { return aiGenerated; }

    public void setAiGenerated(boolean aiGenerated) { this.aiGenerated = aiGenerated; }

    public Instant getCreatedAt() { return createdAt; }
}

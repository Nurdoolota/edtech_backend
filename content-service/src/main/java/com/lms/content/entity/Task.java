package com.lms.content.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> content;

    @Column(name = "lesson_id")
    private Long lessonId;

    @Column(name = "order_index", nullable = false)
    private int orderIndex = 0;

    @Column(length = 500)
    private String title;

    @Column(nullable = false, length = 32)
    private String status = "DRAFT";

    @Column(name = "ai_generated", nullable = false)
    private boolean aiGenerated = false;

    @Column(name = "generation_metadata", columnDefinition = "jsonb")
    private String generationMetadata;

    @Column(name = "unlock_mode", nullable = false, length = 16)
    private String unlockMode = "FREE";

    @Column(name = "prerequisite_task_id")
    private Long prerequisiteTaskId;

    @Column(name = "required_score")
    private Integer requiredScore;

    @Column(name = "prompt_template_id")
    private Long promptTemplateId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public Long getCourseId() { return courseId; }

    public void setCourseId(Long courseId) { this.courseId = courseId; }

    public TaskType getType() { return type; }

    public void setType(TaskType type) { this.type = type; }

    public Map<String, Object> getContent() { return content; }

    public void setContent(Map<String, Object> content) { this.content = content; }

    public Long getLessonId() { return lessonId; }

    public void setLessonId(Long lessonId) { this.lessonId = lessonId; }

    public int getOrderIndex() { return orderIndex; }

    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }

    public String getTitle() { return title; }

    public void setTitle(String title) { this.title = title; }

    public String getStatus() { return status; }

    public void setStatus(String status) { this.status = status; }

    public boolean isAiGenerated() { return aiGenerated; }

    public void setAiGenerated(boolean aiGenerated) { this.aiGenerated = aiGenerated; }

    public String getGenerationMetadata() { return generationMetadata; }

    public void setGenerationMetadata(String generationMetadata) { this.generationMetadata = generationMetadata; }

    public String getUnlockMode() { return unlockMode; }

    public void setUnlockMode(String unlockMode) { this.unlockMode = unlockMode; }

    public Long getPrerequisiteTaskId() { return prerequisiteTaskId; }

    public void setPrerequisiteTaskId(Long prerequisiteTaskId) { this.prerequisiteTaskId = prerequisiteTaskId; }

    public Integer getRequiredScore() { return requiredScore; }

    public void setRequiredScore(Integer requiredScore) { this.requiredScore = requiredScore; }

    public Long getPromptTemplateId() { return promptTemplateId; }

    public void setPromptTemplateId(Long promptTemplateId) { this.promptTemplateId = promptTemplateId; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}

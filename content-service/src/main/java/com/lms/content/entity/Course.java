package com.lms.content.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "courses")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false)
    private String description = "";

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "publish_mode", nullable = false, length = 16)
    private String publishMode = "AUTO";

    @Column(nullable = false, length = 8)
    private String level = "A1";

    @Column(name = "access_status", nullable = false, length = 16)
    private String accessStatus = "OPEN";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }

    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }

    public void setDescription(String description) { this.description = description; }

    public Long getAuthorId() { return authorId; }

    public void setAuthorId(Long authorId) { this.authorId = authorId; }

    public String getPublishMode() { return publishMode; }

    public void setPublishMode(String publishMode) { this.publishMode = publishMode; }

    public String getLevel() { return level; }

    public void setLevel(String level) { this.level = level; }

    public String getAccessStatus() { return accessStatus; }

    public void setAccessStatus(String accessStatus) { this.accessStatus = accessStatus; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}

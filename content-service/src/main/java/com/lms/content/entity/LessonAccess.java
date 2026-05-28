package com.lms.content.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "lesson_access")
public class LessonAccess {

    @EmbeddedId
    private LessonAccessId id;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt = Instant.now();

    public LessonAccess() {}

    public LessonAccess(Long lessonId, Long studentId) {
        this.id = new LessonAccessId(lessonId, studentId);
    }

    public LessonAccessId getId() { return id; }

    public void setId(LessonAccessId id) { this.id = id; }

    public Long getLessonId() { return id.getLessonId(); }

    public Long getStudentId() { return id.getStudentId(); }

    public Instant getGrantedAt() { return grantedAt; }

    public void setGrantedAt(Instant grantedAt) { this.grantedAt = grantedAt; }
}

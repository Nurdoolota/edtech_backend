package com.lms.content.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "course_teachers")
public class CourseTeacher {

    @EmbeddedId
    private CourseTeacherId id;

    @Column(nullable = false, length = 16)
    private String role = "OWNER";

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt = Instant.now();

    public CourseTeacher() {}

    public CourseTeacher(Long courseId, Long userId, String role) {
        this.id = new CourseTeacherId(courseId, userId);
        this.role = role;
    }

    public CourseTeacherId getId() { return id; }

    public void setId(CourseTeacherId id) { this.id = id; }

    public String getRole() { return role; }

    public void setRole(String role) { this.role = role; }

    public Instant getAddedAt() { return addedAt; }

    public void setAddedAt(Instant addedAt) { this.addedAt = addedAt; }
}

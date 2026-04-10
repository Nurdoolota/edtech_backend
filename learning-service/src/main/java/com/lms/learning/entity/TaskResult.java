package com.lms.learning.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "task_results")
public class TaskResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "answer_content", columnDefinition = "jsonb", nullable = false)
    private String answerContent;

    @Column(name = "ai_feedback")
    private String aiFeedback;

    @Column(name = "score", precision = 5, scale = 2)
    private BigDecimal score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ResultStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public Long getTaskId() { return taskId; }

    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public Long getStudentId() { return studentId; }

    public void setStudentId(Long studentId) { this.studentId = studentId; }

    public String getAnswerContent() { return answerContent; }

    public void setAnswerContent(String answerContent) { this.answerContent = answerContent; }

    public String getAiFeedback() { return aiFeedback; }

    public void setAiFeedback(String aiFeedback) { this.aiFeedback = aiFeedback; }

    public BigDecimal getScore() { return score; }

    public void setScore(BigDecimal score) { this.score = score; }

    public ResultStatus getStatus() { return status; }

    public void setStatus(ResultStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}

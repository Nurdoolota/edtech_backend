package com.lms.learning.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "tasks")
@Immutable
public class TaskInfo {

    @Id
    private Long id;

    @Column(name = "lesson_id")
    private Long lessonId;

    @Column(name = "order_index")
    private int orderIndex;

    @Column(name = "unlock_mode")
    private String unlockMode;

    @Column(name = "prerequisite_task_id")
    private Long prerequisiteTaskId;

    @Column(name = "required_score")
    private Integer requiredScore;

    public Long getId() { return id; }

    public Long getLessonId() { return lessonId; }

    public int getOrderIndex() { return orderIndex; }

    public String getUnlockMode() { return unlockMode; }

    public Long getPrerequisiteTaskId() { return prerequisiteTaskId; }

    public Integer getRequiredScore() { return requiredScore; }
}

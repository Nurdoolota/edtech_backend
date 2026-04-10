package com.lms.content.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "group_courses")
public class GroupCourse {

    @EmbeddedId
    private GroupCourseId id;

    public GroupCourse() {}

    public GroupCourse(Long groupId, Long courseId) {
        this.id = new GroupCourseId(groupId, courseId);
    }

    public GroupCourseId getId() { return id; }

    public void setId(GroupCourseId id) { this.id = id; }

    public Long getGroupId() { return id.getGroupId(); }

    public Long getCourseId() { return id.getCourseId(); }
}

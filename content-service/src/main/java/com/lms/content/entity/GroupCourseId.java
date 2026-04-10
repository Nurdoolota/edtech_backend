package com.lms.content.entity;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class GroupCourseId implements Serializable {

    private Long groupId;
    private Long courseId;

    public GroupCourseId() {}

    public GroupCourseId(Long groupId, Long courseId) {
        this.groupId = groupId;
        this.courseId = courseId;
    }

    public Long getGroupId() { return groupId; }

    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public Long getCourseId() { return courseId; }

    public void setCourseId(Long courseId) { this.courseId = courseId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GroupCourseId)) return false;
        GroupCourseId that = (GroupCourseId) o;
        return Objects.equals(groupId, that.groupId) && Objects.equals(courseId, that.courseId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, courseId);
    }
}

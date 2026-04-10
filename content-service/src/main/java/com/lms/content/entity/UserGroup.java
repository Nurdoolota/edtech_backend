package com.lms.content.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_groups")
public class UserGroup {

    @EmbeddedId
    private UserGroupId id;

    public UserGroup() {}

    public UserGroup(Long userId, Long groupId) {
        this.id = new UserGroupId(userId, groupId);
    }

    public UserGroupId getId() { return id; }

    public void setId(UserGroupId id) { this.id = id; }

    public Long getUserId() { return id.getUserId(); }

    public Long getGroupId() { return id.getGroupId(); }
}

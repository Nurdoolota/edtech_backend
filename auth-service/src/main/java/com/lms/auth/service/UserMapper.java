package com.lms.auth.service;

import com.lms.auth.dto.UserResponse;
import com.lms.auth.entity.User;

public final class UserMapper {

    private UserMapper() {}

    public static UserResponse toResponse(User user) {
        String displayName = user.getFirstName() + " " + user.getLastName();
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                displayName,
                user.getRole().getName(),
                user.isActive(),
                user.getAvatarUrl(),
                user.getBio(),
                user.getLocale(),
                user.getTimezone(),
                user.isEmailPrivate(),
                user.getNotifications(),
                user.getLastLoginAt(),
                user.getUpdatedAt(),
                user.getCreatedAt());
    }
}

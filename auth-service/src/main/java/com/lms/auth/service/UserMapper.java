package com.lms.auth.service;

import com.lms.auth.dto.UserResponse;
import com.lms.auth.entity.User;

public final class UserMapper {

    private UserMapper() {}

    public static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole().getName(),
                user.isActive());
    }
}

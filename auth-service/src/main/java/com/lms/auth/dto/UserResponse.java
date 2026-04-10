package com.lms.auth.dto;

import com.lms.auth.entity.RoleName;

public record UserResponse(
        long id, String email, String firstName, String lastName, RoleName role, boolean active) {}

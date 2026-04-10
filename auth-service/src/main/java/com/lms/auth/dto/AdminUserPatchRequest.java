package com.lms.auth.dto;

import com.lms.auth.entity.RoleName;
import jakarta.validation.constraints.AssertTrue;

public record AdminUserPatchRequest(RoleName role, Boolean active) {

    @AssertTrue(message = "At least one of role or active must be set")
    public boolean hasPatch() {
        return role != null || active != null;
    }
}

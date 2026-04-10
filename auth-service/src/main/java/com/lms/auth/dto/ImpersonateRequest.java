package com.lms.auth.dto;

import jakarta.validation.constraints.NotNull;

public record ImpersonateRequest(@NotNull Long targetUserId) {}

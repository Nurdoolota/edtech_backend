package com.lms.content.dto.lesson;

import jakarta.validation.constraints.NotNull;

public record GrantAccessRequest(@NotNull Long studentId) {}

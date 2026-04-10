package com.lms.content.dto.group;

import jakarta.validation.constraints.NotNull;

public record AddStudentRequest(@NotNull Long userId) {}

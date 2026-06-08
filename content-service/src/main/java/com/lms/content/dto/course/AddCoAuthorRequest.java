package com.lms.content.dto.course;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record AddCoAuthorRequest(
        @NotNull Long userId,
        @Pattern(regexp = "OWNER|EDITOR") String role
) {}

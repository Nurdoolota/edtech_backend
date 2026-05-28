package com.lms.content.dto.group;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record BulkAddStudentsRequest(
        @NotEmpty(message = "studentIds must not be empty")
        List<Long> studentIds) {}

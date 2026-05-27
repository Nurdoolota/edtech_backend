package com.lms.content.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ReorderRequest(
        @NotNull @Size(min = 0) List<Long> order
) {}

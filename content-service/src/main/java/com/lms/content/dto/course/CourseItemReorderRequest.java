package com.lms.content.dto.course;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CourseItemReorderRequest(
        @NotNull @Valid List<CourseItemEntry> items
) {
    public record CourseItemEntry(
            @NotNull String type,
            @NotNull Long id
    ) {}
}

package com.lms.content.dto.block;

import com.lms.content.entity.enums.BlockType;
import jakarta.validation.constraints.NotNull;

public record BlockRequest(
        @NotNull BlockType type,
        @NotNull String contentJson,
        boolean aiGenerated,
        Integer orderIndex
) {}

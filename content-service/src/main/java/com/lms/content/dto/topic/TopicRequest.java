package com.lms.content.dto.topic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TopicRequest(
        @NotBlank @Size(max = 500) String title,
        String description,
        Integer orderIndex
) {}

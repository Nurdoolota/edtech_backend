package com.lms.content.dto.lesson;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record TaskReorderRequest(@NotNull List<Long> order) {}

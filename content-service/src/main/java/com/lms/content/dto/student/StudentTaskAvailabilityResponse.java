package com.lms.content.dto.student;

import com.lms.content.entity.TaskType;
import java.time.Instant;

/**
 * Student-facing task availability: includes locked flag, last result info.
 */
public record StudentTaskAvailabilityResponse(
        Long taskId,
        String title,
        TaskType type,
        int orderIndex,
        String unlockMode,
        boolean locked,
        String lockReason,
        String lastStatus,
        Integer lastScore) {}

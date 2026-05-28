package com.lms.content.dto.student;

import java.time.Instant;
import java.util.List;

public record StudentSummaryResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String displayName,
        boolean active,
        List<GroupRef> groups,
        Instant createdAt) {

    public record GroupRef(Long id, String name) {}
}

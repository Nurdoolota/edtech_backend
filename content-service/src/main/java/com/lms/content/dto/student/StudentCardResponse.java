package com.lms.content.dto.student;

import com.lms.content.client.dto.StudentStatsDto;
import com.lms.content.dto.student.StudentSummaryResponse.GroupRef;
import java.time.Instant;
import java.util.List;

public record StudentCardResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String displayName,
        boolean active,
        List<GroupRef> groups,
        Instant createdAt,
        StudentStatsDto stats) {}

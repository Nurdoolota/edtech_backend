package com.lms.content.dto.group;

import java.util.List;

public record BulkAddStudentsResponse(
        List<Long> added,
        List<Long> alreadyInGroup,
        List<Long> notFound) {}

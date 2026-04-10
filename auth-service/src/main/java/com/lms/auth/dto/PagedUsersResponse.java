package com.lms.auth.dto;

import java.util.List;

public record PagedUsersResponse(List<UserResponse> content, long totalElements, int totalPages) {}

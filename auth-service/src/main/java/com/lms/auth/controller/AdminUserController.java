package com.lms.auth.controller;

import com.lms.auth.dto.AdminUserPatchRequest;
import com.lms.auth.dto.PagedUsersResponse;
import com.lms.auth.dto.UserResponse;
import com.lms.auth.entity.RoleName;
import com.lms.auth.exception.ApiBusinessException;
import com.lms.auth.security.JwtUserPrincipal;
import com.lms.auth.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    @Operation(summary = "List users (paginated)")
    public PagedUsersResponse list(
            Authentication authentication,
            @RequestParam(required = false) RoleName role,
            @PageableDefault(size = 20) Pageable pageable) {
        rejectIfImpersonating(authentication);
        return adminUserService.list(role, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by id")
    public UserResponse getById(Authentication authentication, @PathVariable long id) {
        rejectIfImpersonating(authentication);
        return adminUserService.getById(id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Change role and/or active flag")
    public UserResponse patch(
            Authentication authentication,
            @PathVariable long id,
            @Valid @RequestBody AdminUserPatchRequest request) {
        rejectIfImpersonating(authentication);
        return adminUserService.patch(id, request);
    }

    private static void rejectIfImpersonating(Authentication authentication) {
        JwtUserPrincipal p = (JwtUserPrincipal) authentication.getPrincipal();
        if (p.isImpersonating()) {
            throw new ApiBusinessException(
                    "FORBIDDEN",
                    HttpStatus.FORBIDDEN.value(),
                    "Admin operations are not allowed during impersonation");
        }
    }
}

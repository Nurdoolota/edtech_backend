package com.lms.auth.service;

import com.lms.auth.dto.AdminUserPatchRequest;
import com.lms.auth.dto.PagedUsersResponse;
import com.lms.auth.dto.UserResponse;
import com.lms.auth.entity.Role;
import com.lms.auth.entity.User;
import com.lms.auth.exception.ApiBusinessException;
import com.lms.auth.repository.RoleRepository;
import com.lms.auth.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public AdminUserService(UserRepository userRepository, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Transactional(readOnly = true)
    public PagedUsersResponse list(Pageable pageable) {
        Page<User> page = userRepository.findPage(pageable);
        var content = page.getContent().stream().map(UserMapper::toResponse).toList();
        return new PagedUsersResponse(content, page.getTotalElements(), page.getTotalPages());
    }

    @Transactional
    public UserResponse patch(long userId, AdminUserPatchRequest patch) {
        User user =
                userRepository
                        .findByIdWithRole(userId)
                        .orElseThrow(() -> new ApiBusinessException("NOT_FOUND", HttpStatus.NOT_FOUND.value(), "User not found"));
        if (patch.role() != null) {
            Role role =
                    roleRepository
                            .findByName(patch.role())
                            .orElseThrow(
                                    () -> new ApiBusinessException(
                                            "VALIDATION_ERROR",
                                            HttpStatus.BAD_REQUEST.value(),
                                            "Unknown role"));
            user.setRole(role);
        }
        if (patch.active() != null) {
            user.setActive(patch.active());
        }
        return UserMapper.toResponse(user);
    }
}

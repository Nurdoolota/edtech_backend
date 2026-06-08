package com.lms.auth.controller;

import com.lms.auth.dto.PublicProfileResponse;
import com.lms.auth.entity.User;
import com.lms.auth.exception.ApiBusinessException;
import com.lms.auth.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Public user profiles")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get public profile of a user")
    public PublicProfileResponse getById(@PathVariable long id) {
        User user = userRepository.findByIdWithRole(id)
                .orElseThrow(() -> new ApiBusinessException(
                        "NOT_FOUND", HttpStatus.NOT_FOUND.value(), "User not found"));
        String email = user.isEmailPrivate() ? null : user.getEmail();
        return new PublicProfileResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getFirstName() + " " + user.getLastName(),
                user.getRole().getName(),
                user.getAvatarUrl(),
                user.getBio(),
                email,
                user.getCreatedAt());
    }
}

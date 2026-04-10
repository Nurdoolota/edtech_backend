package com.lms.content.controller;

import com.lms.content.dto.PagedResponse;
import com.lms.content.dto.group.AddStudentRequest;
import com.lms.content.dto.group.AssignCourseRequest;
import com.lms.content.dto.group.CreateGroupRequest;
import com.lms.content.dto.group.GroupResponse;
import com.lms.content.dto.group.UpdateGroupRequest;
import com.lms.content.security.JwtUserPrincipal;
import com.lms.content.service.GroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/content/groups")
@Tag(name = "Groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping
    @Operation(summary = "List groups (TEACHER sees own, ADMIN sees all)")
    public PagedResponse<GroupResponse> list(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return groupService.findAll(principal, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get group by id (TEACHER owner or ADMIN)")
    public GroupResponse getById(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        return groupService.findById(id, principal);
    }

    @PostMapping
    @Operation(summary = "Create group (TEACHER, ADMIN)")
    public ResponseEntity<GroupResponse> create(
            @Valid @RequestBody CreateGroupRequest req,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(groupService.create(req, principal));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update group name (owner TEACHER or ADMIN)")
    public GroupResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateGroupRequest req,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        return groupService.update(id, req, principal);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete group (owner TEACHER or ADMIN)")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        groupService.delete(id, principal);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{groupId}/students")
    @Operation(summary = "Add student to group")
    public ResponseEntity<Void> addStudent(
            @PathVariable Long groupId,
            @Valid @RequestBody AddStudentRequest req,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        groupService.addStudent(groupId, req.userId(), principal);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{groupId}/students/{userId}")
    @Operation(summary = "Remove student from group")
    public ResponseEntity<Void> removeStudent(
            @PathVariable Long groupId,
            @PathVariable Long userId,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        groupService.removeStudent(groupId, userId, principal);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{groupId}/courses")
    @Operation(summary = "Assign course to group")
    public ResponseEntity<Void> assignCourse(
            @PathVariable Long groupId,
            @Valid @RequestBody AssignCourseRequest req,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        groupService.assignCourse(groupId, req.courseId(), principal);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{groupId}/courses/{courseId}")
    @Operation(summary = "Remove course from group")
    public ResponseEntity<Void> removeCourse(
            @PathVariable Long groupId,
            @PathVariable Long courseId,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        groupService.removeCourse(groupId, courseId, principal);
        return ResponseEntity.noContent().build();
    }
}

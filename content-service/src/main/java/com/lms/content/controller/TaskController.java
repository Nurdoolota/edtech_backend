package com.lms.content.controller;

import com.lms.content.dto.PagedResponse;
import com.lms.content.dto.task.CreateTaskRequest;
import com.lms.content.dto.task.TaskResponse;
import com.lms.content.dto.task.UpdateTaskRequest;
import com.lms.content.security.JwtUserPrincipal;
import com.lms.content.service.TaskService;
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
@RequestMapping("/api/v1/content/tasks")
@Tag(name = "Tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    @Operation(summary = "List all tasks (TEACHER sees own, ADMIN sees all)")
    public PagedResponse<TaskResponse> list(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return taskService.findAll(principal, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get task by id")
    public TaskResponse getById(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        return taskService.findById(id, principal);
    }

    @PostMapping
    @Operation(summary = "Create task with JSONB content validation (TEACHER, ADMIN)")
    public ResponseEntity<TaskResponse> create(
            @Valid @RequestBody CreateTaskRequest req,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taskService.create(req, principal));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update task (owner TEACHER or ADMIN)")
    public TaskResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTaskRequest req,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        return taskService.update(id, req, principal);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete task (owner TEACHER or ADMIN)")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        taskService.delete(id, principal);
        return ResponseEntity.noContent().build();
    }
}

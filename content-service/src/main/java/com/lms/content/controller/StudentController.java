package com.lms.content.controller;

import com.lms.content.dto.PagedResponse;
import com.lms.content.dto.student.StudentCardResponse;
import com.lms.content.dto.student.StudentSummaryResponse;
import com.lms.content.security.JwtUserPrincipal;
import com.lms.content.service.StudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/content/students")
@Tag(name = "Students")
public class StudentController {

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @GetMapping
    @Operation(summary = "List students (paginated, searchable)")
    public PagedResponse<StudentSummaryResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Boolean notInGroup,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        return studentService.list(q, groupId, notInGroup, pageable, principal);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get student card with learning stats")
    public StudentCardResponse getById(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        return studentService.getById(id, principal);
    }
}

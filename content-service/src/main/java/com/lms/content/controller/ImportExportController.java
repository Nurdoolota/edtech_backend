package com.lms.content.controller;

import com.lms.content.dto.ImportResultResponse;
import com.lms.content.dto.export.CourseExportDto;
import com.lms.content.security.JwtUserPrincipal;
import com.lms.content.service.export.CourseExportService;
import com.lms.content.service.import_.CourseImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/content/courses")
@Tag(name = "Import/Export")
public class ImportExportController {

    private final CourseExportService courseExportService;
    private final CourseImportService courseImportService;

    public ImportExportController(CourseExportService courseExportService,
            CourseImportService courseImportService) {
        this.courseExportService = courseExportService;
        this.courseImportService = courseImportService;
    }

    @GetMapping("/{id}/export")
    @Operation(summary = "Export course as ZIP")
    public void exportZip(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtUserPrincipal principal,
            HttpServletResponse response) {
        courseExportService.exportZip(id, principal, response);
    }

    @GetMapping("/{id}/export.json")
    @Operation(summary = "Export course as JSON")
    public CourseExportDto exportJson(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        return courseExportService.buildExportDto(id, principal);
    }

    @PostMapping("/import")
    @Operation(summary = "Import course from ZIP (multipart/form-data)")
    public ResponseEntity<ImportResultResponse> importCourse(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "targetCourseId", required = false) Long targetCourseId,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        ImportResultResponse result = courseImportService.importCourse(file, targetCourseId, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}

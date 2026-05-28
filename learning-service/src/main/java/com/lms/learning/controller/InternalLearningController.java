package com.lms.learning.controller;

import com.lms.learning.dto.StudentStatsDto;
import com.lms.learning.dto.TaskResultSummaryDto;
import com.lms.learning.service.InternalLearningService;
import java.util.List;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/learning")
public class InternalLearningController {

    private final InternalLearningService internalLearningService;

    public InternalLearningController(InternalLearningService internalLearningService) {
        this.internalLearningService = internalLearningService;
    }

    @GetMapping("/results")
    public ResponseEntity<List<TaskResultSummaryDto>> getResultSummaries(
            @RequestParam List<Long> taskIds,
            @RequestParam Long studentId) {
        if (taskIds.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(
                internalLearningService.getResultSummaries(Set.copyOf(taskIds), studentId));
    }

    @GetMapping("/students/{studentId}/stats")
    public ResponseEntity<StudentStatsDto> getStudentStats(
            @PathVariable Long studentId) {
        return ResponseEntity.ok(internalLearningService.getStudentStats(studentId));
    }
}

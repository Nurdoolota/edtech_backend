package com.lms.learning.service;

import com.lms.learning.dto.TaskResultResponse;
import com.lms.learning.entity.TaskResult;
import org.springframework.stereotype.Component;

@Component
public class TaskResultMapper {

    public TaskResultResponse toResponse(TaskResult tr) {
        return new TaskResultResponse(
                tr.getId(),
                tr.getTaskId(),
                tr.getStudentId(),
                tr.getAnswerContent(),
                tr.getAiFeedback(),
                tr.getScore(),
                tr.getStatus(),
                tr.getCreatedAt(),
                tr.getUpdatedAt());
    }
}

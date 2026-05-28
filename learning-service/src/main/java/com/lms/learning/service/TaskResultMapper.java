package com.lms.learning.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.learning.dto.TaskResultResponse;
import com.lms.learning.entity.TaskResult;
import org.springframework.stereotype.Component;

@Component
public class TaskResultMapper {

    private final ObjectMapper objectMapper;

    public TaskResultMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TaskResultResponse toResponse(TaskResult tr) {
        return new TaskResultResponse(
                tr.getId(),
                tr.getTaskId(),
                tr.getStudentId(),
                answerContentToString(tr.getAnswerContent()),
                tr.getAiFeedback(),
                tr.getAiScore(),
                tr.getAiBreakdown(),
                tr.getTeacherScore(),
                tr.getTeacherFeedback(),
                tr.getScore(),
                tr.getTranscript(),
                tr.getMediaId(),
                tr.getStatus(),
                tr.getCreatedAt(),
                tr.getUpdatedAt());
    }

    private String answerContentToString(JsonNode node) {
        if (node == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize answer_content", e);
        }
    }
}

package com.lms.content.validation;

import com.lms.content.entity.TaskType;
import com.lms.content.exception.ApiBusinessException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Validates that a task's content JSONB contains the required fields for its TaskType.
 * Throws {@link ApiBusinessException} with HTTP 400 on any violation.
 */
@Component
public class ContentJsonValidator {

    public void validate(TaskType type, Map<String, Object> content) {
        if (content == null || content.isEmpty()) {
            throw ApiBusinessException.badRequest("content must not be empty");
        }
        switch (type) {
            case FILL_BLANKS -> {
                requireString(content, "text", type);
                requireArray(content, "answers", type);
            }
            case TRUE_FALSE -> {
                requireArray(content, "questions", type);
                requireArray(content, "correctOptions", type);
            }
            case VIDEO -> {
                requireString(content, "videoUrl", type);
                requireString(content, "transcript", type);
                requireArray(content, "questions", type);
            }
            case TEXT -> {
                requireString(content, "sourceText", type);
                requireArray(content, "questions", type);
                requireString(content, "level", type);
            }
            case TRANSLATION -> {
                requireString(content, "sourceText", type);
                requireString(content, "instructions", type);
            }
            case DEBATES -> {
                requireString(content, "topic", type);
                requireString(content, "botRole", type);
            }
            case LISTENING -> {
                requireString(content, "audioUrl", type);
                requireArray(content, "questions", type);
            }
            case SPEAKING -> {
                requireString(content, "prompt", type);
            }
            case READING_COMPREHENSION -> {
                requireString(content, "sourceText", type);
                requireArray(content, "questions", type);
            }
            case IMAGE_DESCRIPTION -> {
                requireString(content, "imageUrl", type);
                requireString(content, "prompt", type);
            }
        }
    }

    private void requireString(Map<String, Object> content, String field, TaskType type) {
        Object value = content.get(field);
        if (!(value instanceof String s) || s.isBlank()) {
            throw ApiBusinessException.badRequest(
                    "content." + field + " is required and must be a non-blank string for type "
                            + type);
        }
    }

    private void requireArray(Map<String, Object> content, String field, TaskType type) {
        Object value = content.get(field);
        if (!(value instanceof Collection<?> col) || col.isEmpty()) {
            throw ApiBusinessException.badRequest(
                    "content." + field + " is required and must be a non-empty array for type "
                            + type);
        }
    }
}

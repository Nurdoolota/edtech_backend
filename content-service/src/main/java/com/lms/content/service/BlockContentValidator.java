package com.lms.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.content.entity.enums.BlockType;
import com.lms.content.exception.ApiBusinessException;
import org.springframework.stereotype.Component;

@Component
public class BlockContentValidator {

    private final ObjectMapper objectMapper;

    public BlockContentValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void validate(BlockType type, String contentJson) {
        JsonNode node;
        try {
            node = objectMapper.readTree(contentJson);
        } catch (Exception e) {
            throw new ApiBusinessException("INVALID_CONTENT", 400, "contentJson is not valid JSON");
        }

        switch (type) {
            case TEXT -> {
                String format = textOf(node, "format");
                if (!("markdown".equals(format) || "html".equals(format))) {
                    fail("TEXT", "format (\"markdown\" or \"html\"), text");
                }
                if (isBlank(textOf(node, "text"))) {
                    fail("TEXT", "format, text");
                }
            }
            case AUDIO -> {
                if (isBlank(textOf(node, "tts_script")) || isBlank(textOf(node, "voice"))) {
                    fail("AUDIO", "tts_script, voice");
                }
            }
            case IMAGE -> {
                if (isBlank(textOf(node, "caption")) || isBlank(textOf(node, "url"))) {
                    fail("IMAGE", "caption, url");
                }
            }
            case VIDEO -> {
                if (isBlank(textOf(node, "url")) || isBlank(textOf(node, "caption"))) {
                    fail("VIDEO", "url, caption");
                }
            }
        }
    }

    private String textOf(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return (child != null && child.isTextual()) ? child.asText() : null;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private void fail(String typeName, String fields) {
        throw new ApiBusinessException("INVALID_CONTENT", 400,
                "Block type " + typeName + " requires fields: " + fields);
    }
}

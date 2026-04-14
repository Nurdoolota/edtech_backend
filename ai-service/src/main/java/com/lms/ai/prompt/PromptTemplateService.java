package com.lms.ai.prompt;

import com.lms.ai.exception.ApiBusinessException;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class PromptTemplateService {

    private static final Logger log = LoggerFactory.getLogger(PromptTemplateService.class);

    private static final String[] KNOWN_CODES = {
        "text_evaluation",
        "translation_evaluation",
        "video_evaluation",
        "debates_evaluation"
    };

    private final Map<String, String> templates = new ConcurrentHashMap<>();

    @PostConstruct
    void loadTemplates() {
        for (String code : KNOWN_CODES) {
            String path = "prompts/" + code + ".txt";
            try (InputStream is = new ClassPathResource(path).getInputStream()) {
                templates.put(code, new String(is.readAllBytes(), StandardCharsets.UTF_8));
                log.info("Loaded prompt template: {}", code);
            } catch (IOException e) {
                log.error("Failed to load prompt template '{}': {}", code, e.getMessage());
            }
        }
    }

    /**
     * Renders a prompt template by substituting {{KEY}} placeholders.
     *
     * @param templateCode   one of the known prompt_template_code values
     * @param variables      map of placeholder keys (case-insensitive) to values
     * @param studentAnswer  student answer — injected as {{STUDENT_ANSWER}}
     * @return rendered system prompt ready for LLM
     */
    public String render(String templateCode, Map<String, Object> variables, String studentAnswer) {
        String template = templates.get(templateCode);
        if (template == null) {
            throw ApiBusinessException.badRequest("Unknown prompt template code: " + templateCode);
        }

        String result = template;

        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey().toUpperCase() + "}}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(placeholder, value);
        }

        String answer = studentAnswer != null ? studentAnswer : "";
        result = result.replace("{{STUDENT_ANSWER}}", answer);
        // DEBATES uses {{SESSION_TRANSCRIPT}} instead of {{STUDENT_ANSWER}}
        result = result.replace("{{SESSION_TRANSCRIPT}}", answer);

        // remove any remaining unreplaced placeholders (optional fields)
        result = result.replaceAll("\\{\\{[A-Z0-9_]+\\}\\}", "");

        return result;
    }

    /** Visible for testing. */
    Map<String, String> getTemplates() {
        return templates;
    }
}

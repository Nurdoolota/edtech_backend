package com.lms.ai.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lms.ai.exception.ApiBusinessException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PromptTemplateServiceTest {

    private PromptTemplateService service;

    @BeforeEach
    void setUp() {
        service = new PromptTemplateService();
        service.loadTemplates();
    }

    @Test
    void render_substitutesPlaceholders() {
        String result = service.render(
                "text_evaluation",
                Map.of(
                        "SOURCE_TEXT", "The cat sat on the mat.",
                        "QUESTIONS_OR_INSTRUCTIONS", "Summarize.",
                        "CRITERIA_AND_LEVEL", "B1"),
                "The cat was on the mat.");

        assertThat(result).contains("The cat sat on the mat.");
        assertThat(result).contains("Summarize.");
        assertThat(result).contains("B1");
        assertThat(result).contains("The cat was on the mat.");
        assertThat(result).doesNotContain("{{SOURCE_TEXT}}");
        assertThat(result).doesNotContain("{{STUDENT_ANSWER}}");
    }

    @Test
    void render_missingKeyBecomesEmptyString() {
        String result = service.render(
                "text_evaluation",
                Map.of("SOURCE_TEXT", "Some text."),
                "My answer.");

        assertThat(result).doesNotContain("{{QUESTIONS_OR_INSTRUCTIONS}}");
        assertThat(result).doesNotContain("{{CRITERIA_AND_LEVEL}}");
        assertThat(result).contains("My answer.");
    }

    @Test
    void render_unknownTemplateCode_throwsBadRequest() {
        assertThatThrownBy(() ->
                service.render("nonexistent_template", Map.of(), "answer"))
                .isInstanceOf(ApiBusinessException.class)
                .hasMessageContaining("nonexistent_template");
    }

    @Test
    void render_allFourTemplatesLoad() {
        assertThat(service.getTemplates()).containsKeys(
                "text_evaluation",
                "translation_evaluation",
                "video_evaluation",
                "debates_evaluation");
    }

    @Test
    void render_debatesTemplate_substitutesCorrectly() {
        String result = service.render(
                "debates_evaluation",
                Map.of(
                        "TOPIC_AND_ROLES", "Debate about climate change.",
                        "COMPLETION_CRITERIA", "3 rounds"),
                "[student]: I think climate change is real.");

        assertThat(result).contains("Debate about climate change.");
        assertThat(result).contains("3 rounds");
        assertThat(result).contains("[student]: I think climate change is real.");
        assertThat(result).doesNotContain("{{TOPIC_AND_ROLES}}");
        assertThat(result).doesNotContain("{{SESSION_TRANSCRIPT}}");
    }
}

package com.lms.content.validation;

import com.lms.content.entity.TaskType;
import com.lms.content.exception.ApiBusinessException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentJsonValidatorTest {

    private final ContentJsonValidator validator = new ContentJsonValidator();

    // --- FILL_BLANKS ---

    @Test
    void fillBlanks_valid() {
        assertThatNoException().isThrownBy(() ->
                validator.validate(TaskType.FILL_BLANKS,
                        Map.of("text", "Complete ___ sentence.", "answers", List.of("the"))));
    }

    @Test
    void fillBlanks_missingText_throws() {
        assertThatThrownBy(() ->
                validator.validate(TaskType.FILL_BLANKS,
                        Map.of("answers", List.of("the"))))
                .isInstanceOf(ApiBusinessException.class)
                .hasMessageContaining("content.text");
    }

    @Test
    void fillBlanks_missingAnswers_throws() {
        assertThatThrownBy(() ->
                validator.validate(TaskType.FILL_BLANKS, Map.of("text", "Complete ___ sentence.")))
                .isInstanceOf(ApiBusinessException.class)
                .hasMessageContaining("content.answers");
    }

    // --- TRUE_FALSE ---

    @Test
    void trueFalse_valid() {
        assertThatNoException().isThrownBy(() ->
                validator.validate(TaskType.TRUE_FALSE,
                        Map.of("questions", List.of("The sky is blue."),
                                "correctOptions", List.of("TRUE"))));
    }

    @Test
    void trueFalse_missingQuestions_throws() {
        assertThatThrownBy(() ->
                validator.validate(TaskType.TRUE_FALSE,
                        Map.of("correctOptions", List.of("TRUE"))))
                .isInstanceOf(ApiBusinessException.class)
                .hasMessageContaining("content.questions");
    }

    @Test
    void trueFalse_missingCorrectOptions_throws() {
        assertThatThrownBy(() ->
                validator.validate(TaskType.TRUE_FALSE,
                        Map.of("questions", List.of("Q?"))))
                .isInstanceOf(ApiBusinessException.class)
                .hasMessageContaining("content.correctOptions");
    }

    @Test
    void trueFalse_emptyCorrectOptions_throws() {
        assertThatThrownBy(() ->
                validator.validate(TaskType.TRUE_FALSE,
                        Map.of("questions", List.of("Q?"),
                                "correctOptions", List.of())))
                .isInstanceOf(ApiBusinessException.class)
                .hasMessageContaining("content.correctOptions");
    }

    // --- VIDEO ---

    @Test
    void video_valid() {
        assertThatNoException().isThrownBy(() ->
                validator.validate(TaskType.VIDEO,
                        Map.of("videoUrl", "https://youtu.be/abc",
                                "transcript", "Narrator says...",
                                "questions", List.of("What did narrator say?"))));
    }

    @Test
    void video_missingVideoUrl_throws() {
        assertThatThrownBy(() ->
                validator.validate(TaskType.VIDEO,
                        Map.of("transcript", "text", "questions", List.of("Q"))))
                .isInstanceOf(ApiBusinessException.class)
                .hasMessageContaining("content.videoUrl");
    }

    // --- TEXT ---

    @Test
    void text_valid() {
        assertThatNoException().isThrownBy(() ->
                validator.validate(TaskType.TEXT,
                        Map.of("sourceText", "Once upon a time...",
                                "questions", List.of("Summarise the text."),
                                "level", "B2")));
    }

    @Test
    void text_missingLevel_throws() {
        assertThatThrownBy(() ->
                validator.validate(TaskType.TEXT,
                        Map.of("sourceText", "text", "questions", List.of("Q"))))
                .isInstanceOf(ApiBusinessException.class)
                .hasMessageContaining("content.level");
    }

    @Test
    void text_blankLevel_throws() {
        assertThatThrownBy(() ->
                validator.validate(TaskType.TEXT,
                        Map.of("sourceText", "text", "questions", List.of("Q"), "level", "  ")))
                .isInstanceOf(ApiBusinessException.class)
                .hasMessageContaining("content.level");
    }

    // --- TRANSLATION ---

    @Test
    void translation_valid() {
        assertThatNoException().isThrownBy(() ->
                validator.validate(TaskType.TRANSLATION,
                        Map.of("sourceText", "Привет, мир!", "instructions", "Translate to English.")));
    }

    @Test
    void translation_missingInstructions_throws() {
        assertThatThrownBy(() ->
                validator.validate(TaskType.TRANSLATION, Map.of("sourceText", "Текст")))
                .isInstanceOf(ApiBusinessException.class)
                .hasMessageContaining("content.instructions");
    }

    @Test
    void translation_blankInstructions_throws() {
        assertThatThrownBy(() ->
                validator.validate(TaskType.TRANSLATION,
                        Map.of("sourceText", "Текст", "instructions", "   ")))
                .isInstanceOf(ApiBusinessException.class)
                .hasMessageContaining("content.instructions");
    }

    // --- DEBATES ---

    @Test
    void debates_valid() {
        assertThatNoException().isThrownBy(() ->
                validator.validate(TaskType.DEBATES,
                        Map.of("topic", "Is remote work better?", "botRole", "opponent")));
    }

    @Test
    void debates_missingTopic_throws() {
        assertThatThrownBy(() ->
                validator.validate(TaskType.DEBATES, Map.of("botRole", "opponent")))
                .isInstanceOf(ApiBusinessException.class)
                .hasMessageContaining("content.topic");
    }

    @Test
    void debates_missingBotRole_throws() {
        assertThatThrownBy(() ->
                validator.validate(TaskType.DEBATES, Map.of("topic", "AI ethics")))
                .isInstanceOf(ApiBusinessException.class)
                .hasMessageContaining("content.botRole");
    }

    // --- null / empty content ---

    @Test
    void nullContent_throws() {
        assertThatThrownBy(() -> validator.validate(TaskType.FILL_BLANKS, null))
                .isInstanceOf(ApiBusinessException.class);
    }

    @Test
    void emptyContent_throws() {
        assertThatThrownBy(() -> validator.validate(TaskType.TEXT, Map.of()))
                .isInstanceOf(ApiBusinessException.class);
    }
}

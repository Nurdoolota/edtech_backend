package com.lms.learning.service.checker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.lms.learning.entity.TaskType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TaskCheckerFactoryTest {

    private final KeyBasedChecker keyBasedChecker = mock(KeyBasedChecker.class);
    private final AiBasedChecker aiBasedChecker = mock(AiBasedChecker.class);
    private final TaskCheckerFactory factory = new TaskCheckerFactory(keyBasedChecker, aiBasedChecker);

    @Test
    void getChecker_fillBlanks_returnsKeyBasedChecker() {
        assertThat(factory.getChecker(TaskType.FILL_BLANKS)).isSameAs(keyBasedChecker);
    }

    @Test
    void getChecker_trueFalse_returnsKeyBasedChecker() {
        assertThat(factory.getChecker(TaskType.TRUE_FALSE)).isSameAs(keyBasedChecker);
    }

    @ParameterizedTest
    @EnumSource(value = TaskType.class, names = {"TEXT", "TRANSLATION", "VIDEO", "DEBATES",
            "READING_COMPREHENSION", "IMAGE_DESCRIPTION"})
    void getChecker_aiTypes_returnsAiBasedChecker(TaskType type) {
        assertThat(factory.getChecker(type)).isSameAs(aiBasedChecker);
    }

    @ParameterizedTest
    @EnumSource(value = TaskType.class, names = {"SPEAKING", "LISTENING"})
    void getChecker_audioTypes_throwsIllegalArgument(TaskType type) {
        assertThatThrownBy(() -> factory.getChecker(type))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No checker registered for task type");
    }
}

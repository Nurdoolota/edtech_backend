package com.lms.learning.service.checker;

import com.lms.learning.entity.TaskType;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TaskCheckerFactory {

    private final Map<TaskType, TaskChecker> checkers;

    public TaskCheckerFactory(KeyBasedChecker keyBasedChecker, AiBasedChecker aiBasedChecker) {
        checkers = new EnumMap<>(TaskType.class);
        checkers.put(TaskType.FILL_BLANKS, keyBasedChecker);
        checkers.put(TaskType.TRUE_FALSE, keyBasedChecker);
        checkers.put(TaskType.TEXT, aiBasedChecker);
        checkers.put(TaskType.TRANSLATION, aiBasedChecker);
        checkers.put(TaskType.VIDEO, aiBasedChecker);
        checkers.put(TaskType.DEBATES, aiBasedChecker);
        checkers.put(TaskType.READING_COMPREHENSION, aiBasedChecker);
        checkers.put(TaskType.IMAGE_DESCRIPTION, aiBasedChecker);
        // LISTENING and SPEAKING use manual-review flow — no checker registered.
        // LearningService.submitAnswer short-circuits for those types before calling getChecker.
    }

    public TaskChecker getChecker(TaskType type) {
        TaskChecker checker = checkers.get(type);
        if (checker == null) {
            throw new IllegalArgumentException("No checker registered for task type: " + type);
        }
        return checker;
    }
}

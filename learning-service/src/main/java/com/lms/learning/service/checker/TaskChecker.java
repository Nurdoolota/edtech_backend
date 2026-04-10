package com.lms.learning.service.checker;

import com.lms.learning.entity.Task;

public interface TaskChecker {

    EvaluationResult check(Task task, String answerContent);
}

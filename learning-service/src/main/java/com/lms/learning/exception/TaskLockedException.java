package com.lms.learning.exception;

public class TaskLockedException extends RuntimeException {

    private final String reason;

    public TaskLockedException(String reason) {
        super("Task is locked: " + reason);
        this.reason = reason;
    }

    public String getReason() { return reason; }
}

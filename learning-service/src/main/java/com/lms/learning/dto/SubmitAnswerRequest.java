package com.lms.learning.dto;

import jakarta.validation.constraints.NotBlank;

public record SubmitAnswerRequest(
        @NotBlank(message = "answer_content must not be blank")
        String answerContent) {}

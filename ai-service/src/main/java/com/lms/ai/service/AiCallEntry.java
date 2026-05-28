package com.lms.ai.service;

/**
 * Carries the data needed to persist one AI call log row.
 * Populated by callers (AI-04 generation endpoints) and passed to {@link AiCallLogger#log}.
 *
 * @param userId     ID of the authenticated user who triggered the call (may be {@code null} for
 *                   unauthenticated / system calls)
 * @param endpoint   the HTTP endpoint path that was hit (e.g. {@code /internal/ai/evaluate})
 * @param model      LLM model identifier used for this call
 * @param latencyMs  wall-clock duration of the LLM HTTP call in milliseconds
 * @param tokensIn   prompt tokens consumed (may be {@code null} when the LLM response
 *                   does not return usage data)
 * @param tokensOut  completion tokens produced (may be {@code null})
 * @param status     one of {@code SUCCESS}, {@code ERROR}, {@code TIMEOUT}
 * @param error      error message when status is not SUCCESS; may contain API keys — they will
 *                   be redacted by {@link AiCallLogger} before persistence
 */
public record AiCallEntry(
        Long userId,
        String endpoint,
        String model,
        int latencyMs,
        Integer tokensIn,
        Integer tokensOut,
        String status,
        String error
) {
}

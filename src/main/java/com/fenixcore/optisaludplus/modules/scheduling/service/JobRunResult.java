package com.fenixcore.optisaludplus.modules.scheduling.service;

import java.util.HashMap;
import java.util.Map;

/**
 * Outcome of a single {@link ScheduledJobRunner#run()} invocation.
 *
 * <p>{@link #success} = {@code true} maps to {@code outcome=SUCCESS} in the
 * {@code scheduled_job_runs} row; {@code false} maps to {@code FAILED} and
 * {@link #errorMessage} is persisted into the row's {@code error_message}
 * column.</p>
 *
 * <p>{@link #summary} is the runner-specific JSONB payload (e.g. for
 * {@code MEMBERSHIP_STATUS_SWEEP}:
 * {@code {scanned=23, suspended=2, expired=1}}). Each runner picks its
 * own shape; the framework persists it as-is.</p>
 */
public record JobRunResult(
        boolean success,
        Map<String, Object> summary,
        String errorMessage
) {

    public static JobRunResult success(Map<String, Object> summary) {
        return new JobRunResult(true, summary != null ? summary : new HashMap<>(), null);
    }

    public static JobRunResult failure(String errorMessage) {
        return new JobRunResult(false, new HashMap<>(), errorMessage);
    }

    public static JobRunResult failure(String errorMessage, Map<String, Object> partialSummary) {
        return new JobRunResult(false, partialSummary != null ? partialSummary : new HashMap<>(), errorMessage);
    }
}

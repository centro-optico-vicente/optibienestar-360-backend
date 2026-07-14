package com.fenixcore.optibienestar360.modules.scheduling.service;

import java.util.UUID;

/**
 * Outcome envelope returned by {@code JobExecutionService.runNow(...)}.
 * Maps to either an HTTP 200 ({@link #synced} = {@code true}, with full
 * {@link #result}) or an HTTP 202 ({@link #synced} = {@code false},
 * {@link #result} is {@code null} and the caller polls
 * {@code GET /scheduled-jobs/{uuid}/runs/{runUuid}} until terminal).
 */
public record ManualRunResult(
        UUID runUuid,
        boolean synced,
        JobRunResult result
) {

    public static ManualRunResult synced(UUID runUuid, JobRunResult result) {
        return new ManualRunResult(runUuid, true, result);
    }

    public static ManualRunResult async(UUID runUuid) {
        return new ManualRunResult(runUuid, false, null);
    }
}

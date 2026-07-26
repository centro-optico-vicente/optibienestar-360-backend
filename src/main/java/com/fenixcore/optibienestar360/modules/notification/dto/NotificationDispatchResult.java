package com.fenixcore.optibienestar360.modules.notification.dto;

/**
 * Outcome counts of one worker pass over the notification queue. Surfaced by
 * the {@code NotificationDispatchJobRunner} into the {@code scheduled_job_runs}
 * summary.
 *
 * @param processed    rows the worker picked up this pass
 * @param sent         delivered successfully (→ SENT)
 * @param failed       failed but still have retries left (→ FAILED, backoff set)
 * @param deadLettered failed with retries exhausted (→ DEAD_LETTER)
 */
public record NotificationDispatchResult(
        int processed,
        int sent,
        int failed,
        int deadLettered
) {}

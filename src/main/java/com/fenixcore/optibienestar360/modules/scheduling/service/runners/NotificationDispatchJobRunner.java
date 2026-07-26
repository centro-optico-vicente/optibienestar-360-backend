package com.fenixcore.optibienestar360.modules.scheduling.service.runners;

import com.fenixcore.optibienestar360.modules.notification.dto.NotificationDispatchResult;
import com.fenixcore.optibienestar360.modules.notification.service.NotificationService;
import com.fenixcore.optibienestar360.modules.scheduling.service.JobRunResult;
import com.fenixcore.optibienestar360.modules.scheduling.service.ScheduledJobRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Worker runner of the persistent notification queue (V28 / vertical-9).
 * Resolved by code {@code NOTIFICATION_DISPATCH} (seeded in
 * {@code scheduled_jobs} by V40, cron every few minutes). Each pass drains a
 * bounded batch of due rows — {@code PENDING} whose {@code scheduled_for} has
 * arrived plus {@code FAILED} whose backoff has elapsed — delegating the send
 * + state transitions to {@link NotificationService#dispatchDue(int)}.
 *
 * <p>Seed the job with {@code allow_concurrent=false} so two passes never
 * claim the same row (the send itself is synchronous and per-row, so a single
 * bad recipient does not stall the batch).</p>
 */
@Component
@RequiredArgsConstructor
public class NotificationDispatchJobRunner implements ScheduledJobRunner {

    public static final String CODE = "NOTIFICATION_DISPATCH";

    /** Bounded batch so a large backlog drains over several passes rather than one long transaction. */
    private static final int BATCH_SIZE = 100;

    private final NotificationService notificationService;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public JobRunResult run() {
        NotificationDispatchResult result = notificationService.dispatchDue(BATCH_SIZE);
        Map<String, Object> summary = Map.of(
                "processed", result.processed(),
                "sent", result.sent(),
                "failed", result.failed(),
                "deadLettered", result.deadLettered());
        return JobRunResult.success(summary);
    }
}

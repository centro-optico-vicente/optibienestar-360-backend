package com.fenixcore.optibienestar360.modules.scheduling.dto;

import com.fenixcore.optibienestar360.core.display.Display;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Output DTO for {@code GET /v1/admin/scheduled-jobs/{uuid}/runs} (history)
 * and {@code GET /v1/admin/scheduled-jobs/{uuid}/runs/{runUuid}} (single,
 * used by the frontend for polling the async manual trigger).
 *
 * <p>{@code jobCode} is included so a "recent runs across all jobs" view
 * can render the source without joining back. Scalars carry a localized
 * {@code _Display} sibling (ADR 0014).</p>
 */
public record ScheduledJobRunDto(
        UUID uuid,
        UUID jobUuid,
        String jobCode,

        @Display(Display.Kind.DATETIME) Instant startedAt,
        @Display(Display.Kind.DATETIME) Instant finishedAt,
        Long durationMs,

        @Display(value = Display.Kind.ENUM, enumScope = "scheduled_job_run.outcome") String outcome,
        String triggeredBy,
        UUID triggeredByUserUuid,

        Map<String, Object> summary,
        String errorMessage,
        int attemptCount,

        @Display(Display.Kind.DATETIME) Instant createdAt
) {}

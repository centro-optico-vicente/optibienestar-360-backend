package com.fenixcore.optisaludplus.modules.scheduling.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Output DTO for {@code GET /v1/admin/scheduled-jobs/{uuid}/runs} (history)
 * and {@code GET /v1/admin/scheduled-jobs/{uuid}/runs/{runUuid}} (single,
 * used by the frontend for polling the async manual trigger).
 *
 * <p>{@code jobCode} is included so a "recent runs across all jobs" view
 * can render the source without joining back.</p>
 */
public record ScheduledJobRunDto(
        UUID uuid,
        UUID jobUuid,
        String jobCode,

        Instant startedAt,
        Instant finishedAt,
        Long durationMs,

        String outcome,
        String triggeredBy,
        UUID triggeredByUserUuid,

        Map<String, Object> summary,
        String errorMessage,

        Instant createdAt
) {}

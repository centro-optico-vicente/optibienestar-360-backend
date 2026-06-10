package com.fenixcore.optisaludplus.modules.scheduling.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Output DTO for {@code GET /v1/admin/scheduled-jobs} (list) and
 * {@code GET /v1/admin/scheduled-jobs/{uuid}} (detail). Flat record — the
 * last-run snapshot is embedded so the admin list view doesn't need a
 * follow-up call per row.
 */
public record ScheduledJobDto(
        UUID uuid,
        String code,
        String displayName,
        String description,

        // Schedule
        String cronExpression,
        String timezone,
        boolean enabled,

        // Execution policy
        boolean allowConcurrent,
        int maxSyncSeconds,
        boolean lockHeld,

        // Last-run snapshot
        Instant lastRunAt,
        String lastRunStatus,
        Instant nextRunAt,

        // Audit
        boolean active,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}

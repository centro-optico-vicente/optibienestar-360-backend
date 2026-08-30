package com.fenixcore.optibienestar360.modules.scheduling.dto;

import com.fenixcore.optibienestar360.core.display.Display;

import java.time.Instant;
import java.util.UUID;

/**
 * Output DTO for {@code GET /v1/admin/scheduled-jobs} (list) and
 * {@code GET /v1/admin/scheduled-jobs/{uuid}} (detail). Flat record — the
 * last-run snapshot is embedded so the admin list view doesn't need a
 * follow-up call per row. Presentational scalars carry a localized
 * {@code _Display} sibling (hub ADR 0014).
 */
public record ScheduledJobDto(
        UUID uuid,
        String code,
        String displayName,
        String description,

        // Schedule
        String cronExpression,
        String timezone,
        @Display(Display.Kind.BOOLEAN) boolean enabled,

        // Execution policy
        @Display(Display.Kind.BOOLEAN) boolean allowConcurrent,
        int maxSyncSeconds,
        @Display(Display.Kind.BOOLEAN) boolean lockHeld,

        // Last-run snapshot
        @Display(Display.Kind.DATETIME) Instant lastRunAt,
        @Display(value = Display.Kind.ENUM, enumScope = "scheduled_job.last_run_status") String lastRunStatus,
        @Display(Display.Kind.DATETIME) Instant nextRunAt,

        // Audit
        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(value = Display.Kind.ENUM, enumScope = "scheduled_job.status") String status,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        @Display(Display.Kind.DATETIME) Instant updatedAt
) {}

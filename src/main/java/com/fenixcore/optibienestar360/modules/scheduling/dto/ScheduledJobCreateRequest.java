package com.fenixcore.optibienestar360.modules.scheduling.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload for {@code POST /v1/admin/scheduled-jobs}. Cron + timezone are
 * required and validated for structural correctness; semantic checks
 * ({@code CronExpression.parse}, {@code ZoneId.of}) happen in the service
 * before the row is persisted.
 *
 * <p>{@code code} is constrained to UPPER_SNAKE_CASE to match the runner
 * resolution convention ({@code MEMBERSHIP_STATUS_SWEEP}, etc.).</p>
 */
public record ScheduledJobCreateRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,79}$", message = "{validation.code.uppercase.long}")
        String code,

        @NotBlank @Size(max = 120) String displayName,
        String description,

        @NotBlank @Size(max = 120) String cronExpression,
        @NotBlank @Size(max = 60) String timezone,

        Boolean enabled,                // default true server-side
        Boolean allowConcurrent,        // default false server-side
        @Min(0) Integer maxSyncSeconds  // default 30 server-side
) {}

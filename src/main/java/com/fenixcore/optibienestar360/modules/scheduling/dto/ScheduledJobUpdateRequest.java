package com.fenixcore.optibienestar360.modules.scheduling.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Payload for {@code PUT /v1/admin/scheduled-jobs/{uuid}}. PATCH semantics —
 * only non-null fields are applied. After commit the
 * {@code DynamicScheduledJobsRegistry} is re-pushed via the after-commit
 * hook so the cron / enabled change takes effect immediately.
 *
 * <p>Renaming {@code code} is not allowed (the registry resolves runners by
 * code and changing it mid-stream would break that link). To "rename"
 * effectively, soft-delete the old row and create a new one with the
 * desired code.</p>
 */
public record ScheduledJobUpdateRequest(
        @Size(max = 120) String displayName,
        String description,

        @Size(max = 120) String cronExpression,
        @Pattern(regexp = "^[A-Za-z]+/[A-Za-z_]+(/[A-Za-z_]+)*$|^UTC$|^GMT([+-]\\d{1,2}(:\\d{2})?)?$",
                 message = "{scheduled_job.timezone.invalid}")
        @Size(max = 60) String timezone,

        Boolean enabled,
        Boolean allowConcurrent,
        @Min(0) Integer maxSyncSeconds,

        // Per-job config (V94) — null means "leave as-is", matching PATCH
        // semantics of every other field here; pass {} explicitly to clear it.
        Map<String, Object> parameters,

        // Audit knobs (allow disabling without touching enabled flag — e.g.
        // soft-deleting from the admin UI sets active=false directly).
        Boolean active,
        String status
) {}

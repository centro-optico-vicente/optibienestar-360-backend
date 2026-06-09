package com.fenixcore.optisaludplus.modules.scheduling.entity;

import com.fenixcore.optisaludplus.core.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Configuration of a runtime-managed scheduled job. The {@link #code} is the
 * lookup key the app uses to resolve a row to its
 * {@code ScheduledJobRunner} bean — admin can edit cron / timezone /
 * enabled at runtime and the {@code DynamicScheduledJobsRegistry}
 * re-registers the underlying {@code ScheduledFuture} after the commit.
 *
 * <p>Concurrency policy:</p>
 * <ul>
 *   <li>{@link #allowConcurrent} — when {@code false} (default), a new run
 *       (scheduled or manual) is rejected while a previous {@code RUNNING}
 *       row exists in {@code scheduled_job_runs}.</li>
 *   <li>{@link #maxSyncSeconds} — caps how long the manual {@code /run-now}
 *       HTTP request waits for the runner before bailing to HTTP 202 +
 *       {@code runUuid} for polling. {@code 0} = always async; large
 *       values = effectively always sync.</li>
 *   <li>{@link #lockHeld} — soft mutex. Today informational (single replica);
 *       in a future multi-replica HA it'll be backed by
 *       {@code pg_try_advisory_lock(hashtext('scheduled_job:' || code))}.</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "scheduled_jobs")
@AttributeOverride(name = "id", column = @Column(name = "scheduled_jobs_id", nullable = false, updatable = false))
public class ScheduledJob extends BaseEntity {

    @Column(length = 80, unique = true, nullable = false)
    private String code;

    @Column(name = "display_name", length = 120, nullable = false)
    private String displayName;

    @Column(columnDefinition = "text")
    private String description;

    // ─── Schedule ───────────────────────────────────────────────────────────

    @Column(name = "cron_expression", length = 120, nullable = false)
    private String cronExpression;

    @Column(length = 60, nullable = false)
    private String timezone = "America/Caracas";

    @Column(nullable = false)
    private boolean enabled = true;

    // ─── Execution policy ───────────────────────────────────────────────────

    @Column(name = "allow_concurrent", nullable = false)
    private boolean allowConcurrent = false;

    @Column(name = "max_sync_seconds", nullable = false)
    private int maxSyncSeconds = 30;

    @Column(name = "lock_held", nullable = false)
    private boolean lockHeld = false;

    // ─── Last-run snapshot (cache; full history in scheduled_job_runs) ─────

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "last_run_status", length = 20)
    private String lastRunStatus;

    @Column(name = "next_run_at")
    private Instant nextRunAt;

    /** Values for {@link BaseEntity#getStatus()} pinned by the V22 CHECK. */
    public enum JobStatus {
        ENABLED, DISABLED
    }
}

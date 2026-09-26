package com.fenixcore.optibienestar360.modules.scheduling.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

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

    /**
     * Defaults to the app's own timezone ({@link AppTimeZone#ZONE}, env
     * {@code TZ}, falls back to {@code America/Caracas}) instead of a
     * hardcoded literal (hub plan competitive-commission-rules, Fase A, H2)
     * — a job created without specifying this in-code (tests, a new seed
     * migration) still gets a value consistent with the deployed env
     * instead of silently pinning to Caracas.
     */
    @Column(length = 60, nullable = false)
    private String timezone = AppTimeZone.ZONE.getId();

    @Column(nullable = false)
    private boolean enabled = true;

    // ─── Execution policy ───────────────────────────────────────────────────

    @Column(name = "allow_concurrent", nullable = false)
    private boolean allowConcurrent = false;

    @Column(name = "max_sync_seconds", nullable = false)
    private int maxSyncSeconds = 30;

    @Column(name = "lock_held", nullable = false)
    private boolean lockHeld = false;

    /**
     * Extra tries after the first failure before giving up (0 = no retry).
     * Applied by {@code JobExecutionService} around every
     * {@code ScheduledJobRunner.run()} call, scheduled or manual. Defaults to
     * 5 (V1XX) so a new job created without specifying this field still gets
     * a sane retry policy instead of silently retrying zero times.
     */
    @Column(name = "max_retry_attempts", nullable = false)
    private int maxRetryAttempts = 5;

    /** Fixed wait between retry attempts. Ignored when {@link #maxRetryAttempts} is 0. Defaults to 10s (V1XX). */
    @Column(name = "retry_delay_seconds", nullable = false)
    private int retryDelaySeconds = 10;

    /**
     * Free-form per-job configuration (V94) — e.g. {@code FETCH_EXCHANGE_RATES}
     * carries {@code {"baseUrl": "https://rates-api.jeaninformatico.com"}}.
     * Lets a runner needing external config (a URL, a batch size, ...) read it
     * from its own row instead of a new {@code app.*} Spring property per
     * integration — admin-editable via {@code PUT /v1/admin/scheduled-jobs/{uuid}}
     * with no redeploy. Same JSONB-via-{@link JdbcTypeCode}(SqlTypes.JSON)
     * precedent {@link ScheduledJobRun#getSummary()} already established.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> parameters = new HashMap<>();

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

package com.fenixcore.optisaludplus.modules.scheduling.entity;

import com.fenixcore.optisaludplus.core.entity.BaseAuditEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable audit ledger of every scheduled-job execution attempt. One row
 * per attempt; the {@link #outcome} captures the terminal state of the
 * run (or {@code RUNNING} while in flight).
 *
 * <p>{@link #summary} is a per-runner JSONB payload — e.g. for the
 * membership status sweep:
 * <pre>{@code {"scanned": 23, "suspended": 2, "expired": 1}}</pre>
 * Letting each runner pick its own shape avoids a column-per-job and keeps
 * the framework table runner-agnostic.</p>
 *
 * <p>{@link #triggeredByUserUuid} is required when {@link #triggeredBy} is
 * {@link TriggerSource#MANUAL} (V22 CHECK enforces this) so the audit
 * trail never loses actor identity for manual interventions.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "scheduled_job_runs")
@AttributeOverride(name = "id", column = @Column(name = "scheduled_job_runs_id", nullable = false, updatable = false))
public class ScheduledJobRun extends BaseAuditEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scheduled_job_id", nullable = false)
    private ScheduledJob scheduledJob;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(length = 20, nullable = false)
    private String outcome = Outcome.RUNNING.name();

    @Column(name = "triggered_by", length = 20, nullable = false)
    private String triggeredBy;

    @Column(name = "triggered_by_user_uuid")
    private UUID triggeredByUserUuid;

    /**
     * Per-runner result payload. Persisted as JSONB. Hibernate 6 maps it
     * via {@link JdbcTypeCode}(SqlTypes.JSON) — no extra dialect dependency.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> summary;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    /** Terminal states the V22 CHECK pins {@link #outcome} to (plus {@code RUNNING}). */
    public enum Outcome {
        RUNNING, SUCCESS, FAILED, TIMEOUT, SKIPPED_CONCURRENT, CANCELED
    }

    /** How the run was started. */
    public enum TriggerSource {
        /** The dynamic registry's cron trigger fired this run. */
        SCHEDULED,
        /** An admin invoked {@code POST /run-now} directly. */
        MANUAL,
        /** Reserved for future startup-replay of jobs interrupted by restart. */
        STARTUP
    }
}

package com.fenixcore.optibienestar360.modules.membership.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Concrete subscription of a {@link Member} to a {@link Plan}. The row
 * payments are billed against.
 *
 * <p>Lifecycle (mirrors the V21 CHECK constraint):</p>
 * <pre>
 *     ACTIVE → SUSPENDED → EXPIRED   (driven by the daily status job)
 *          ↘ CANCELED                  (admin action)
 * </pre>
 *
 * <p>{@code status} (the lifecycle column) is inherited from
 * {@link BaseEntity} as a {@code String} — same pattern used by
 * {@code AllyAgreement}. Service code references the values via the
 * {@link LifecycleStatus} enum to keep callers type-safe; e.g.:
 * <pre>
 *     membership.setStatus(Membership.LifecycleStatus.SUSPENDED.name());
 * </pre>
 * The V21 CHECK constraint enforces that only the four enum values land in
 * the column at DB level.</p>
 *
 * <p>Pricing is snapshotted at enrollment ({@code inscriptionFee},
 * {@code monthlyFee}, {@code gracePeriodDays} all copied from the parent
 * Plan at the time of {@link #setPlan(Plan)}/creation) — if the parent
 * Plan later edits its pricing, the active Membership keeps what was
 * originally agreed. Renegotiation = cancel the current row + create a
 * new one, audit trail clean.</p>
 *
 * <p>{@code lastStatusChangeAt} / {@code lastStatusChangeReason} record the
 * most recent transition only. A full history can land in a future
 * {@code membership_status_log} table if compliance requires it.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "memberships")
@AttributeOverride(name = "id", column = @Column(name = "memberships_id", nullable = false, updatable = false))
public class Membership extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    // ─── Lifecycle dates ────────────────────────────────────────────────────

    @Column(name = "enrolled_at", nullable = false)
    private LocalDate enrolledAt = LocalDate.now();

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @Column(name = "next_due_date", nullable = false)
    private LocalDate nextDueDate;

    @Column(name = "last_paid_through")
    private LocalDate lastPaidThrough;

    // ─── Pricing snapshot (immune to later plan edits) ─────────────────────

    @Column(name = "inscription_fee", precision = 10, scale = 2, nullable = false)
    private BigDecimal inscriptionFee;

    @Column(name = "monthly_fee", precision = 10, scale = 2, nullable = false)
    private BigDecimal monthlyFee;

    @Column(name = "grace_period_days", nullable = false)
    private int gracePeriodDays;

    // ─── Status transition audit (last transition only) ────────────────────

    @Column(name = "last_status_change_at")
    private Instant lastStatusChangeAt;

    @Column(name = "last_status_change_reason", columnDefinition = "text")
    private String lastStatusChangeReason;

    /**
     * Lifecycle values that may land in {@link BaseEntity#getStatus()}.
     * Defined as an enum to keep service callers type-safe — the column
     * itself is a {@code VARCHAR(50)} with a CHECK constraint pinning it to
     * exactly these four values (see V21).
     */
    public enum LifecycleStatus {
        ACTIVE, SUSPENDED, EXPIRED, CANCELED
    }
}

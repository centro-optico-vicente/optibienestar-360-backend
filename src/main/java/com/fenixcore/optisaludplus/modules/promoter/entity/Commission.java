package com.fenixcore.optisaludplus.modules.promoter.entity;

import com.fenixcore.optisaludplus.core.entity.BaseEntity;
import com.fenixcore.optisaludplus.modules.member.entity.Member;
import com.fenixcore.optisaludplus.modules.payment.entity.Payment;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Earnings ledger (V26). One row per payment-driven commission event,
 * carrying the tier snapshot inline so later edits to commission_tiers
 * (future v2 table) do NOT retroactively rewrite history.
 *
 * <p>{@link #commissionPct} XOR {@link #flatAmount} — exactly one is
 * populated (V26 CHECK {@code chk_commissions_pct_xor_flat}). Service
 * code computes one or the other from the matched tier at calculation
 * time.</p>
 *
 * <p>{@link #commissionTierId} is mapped as a bare {@code Long} (no FK)
 * because {@code commission_tiers} doesn't exist yet (v2). When it
 * lands, switch to {@code @ManyToOne CommissionTier} and the schema
 * gets the deferred FK from V26 too — closure trigger documented in
 * vertical-8 § Pendientes de análisis.</p>
 *
 * <p>{@code status} is inherited as a {@code String}; the V26 CHECK
 * pins it to {@link CommissionStatus} values.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "commissions")
@AttributeOverride(name = "id", column = @Column(name = "commissions_id", nullable = false, updatable = false))
public class Commission extends BaseEntity {

    // ─── Earner + originator ───────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promoter_id", nullable = false)
    private Promoter promoter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    /**
     * Denormalized FK from {@code payment.membership.member} so the
     * "all commissions of member X" query stays 1-hop.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    // ─── Money ─────────────────────────────────────────────────────────────

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(length = 3, nullable = false)
    private String currency = "USD";

    // ─── Calculation snapshot ──────────────────────────────────────────────

    @Column(name = "calculation_basis", precision = 10, scale = 2, nullable = false)
    private BigDecimal calculationBasis;

    @Column(name = "commission_pct", precision = 5, scale = 2)
    private BigDecimal commissionPct;

    @Column(name = "flat_amount", precision = 10, scale = 2)
    private BigDecimal flatAmount;

    /**
     * Bare ID — FK to {@code commission_tiers} lands when that table
     * ships (deferred per vertical-8 § Pendientes de análisis).
     */
    @Column(name = "commission_tier_id")
    private Long commissionTierId;

    @Column(name = "tier_name_snapshot", length = 80)
    private String tierNameSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "applies_to", length = 20, nullable = false)
    private AppliesTo appliesTo;

    // ─── Period ────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "period_strategy", length = 20, nullable = false)
    private PeriodStrategy periodStrategy = PeriodStrategy.MONTHLY;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "earned_at", nullable = false)
    private Instant earnedAt = Instant.now();

    // ─── Payout tracking ───────────────────────────────────────────────────

    @Column(name = "payout_reference", length = 120)
    private String payoutReference;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "void_reason", columnDefinition = "text")
    private String voidReason;

    @Column(name = "admin_notes", columnDefinition = "text")
    private String adminNotes;

    // ─── Inner enums (V26 CHECK constraint values) ─────────────────────────

    public enum AppliesTo {
        INSCRIPTION, MONTHLY
    }

    public enum PeriodStrategy {
        DAILY, WEEKLY, BIWEEKLY, MONTHLY, QUARTERLY, SEMIANNUAL, ANNUAL
    }

    /** Workflow values pinned by the V26 CHECK on {@link BaseEntity#getStatus()}. */
    public enum CommissionStatus {
        PENDING, PAID, VOIDED, DISPUTED
    }
}

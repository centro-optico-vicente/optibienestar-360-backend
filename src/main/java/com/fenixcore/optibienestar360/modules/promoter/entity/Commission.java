package com.fenixcore.optibienestar360.modules.promoter.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

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

    /**
     * Days-late the recurring payment was collected (V47 collection-commission
     * engine). {@code null} for INSCRIPTION rows and for MONTHLY rows computed
     * before the engine shipped / when no collection tier was applicable.
     */
    @Column(name = "collection_days")
    private Integer collectionDays;

    /**
     * FK to {@code collection_commission_tiers} — populated alongside
     * {@link #collectionDays} when the collection-commission engine picked the
     * rate instead of the plan/volume {@link #commissionTierId}.
     */
    @Column(name = "collection_tier_id")
    private Long collectionTierId;

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

    /**
     * Rate to the organization's official currency vigente at {@link #earnedAt}
     * (calculation/closing time). Persisted so a later payout at a different
     * rate can expose the FX gap the company absorbs — see
     * {@link #exchangeRateAtPaid}. Populated by {@code CommissionService} at
     * creation time; {@code null} only when no rate was vigente for that pair.
     */
    @Column(name = "exchange_rate_at_earned", precision = 18, scale = 8)
    private BigDecimal exchangeRateAtEarned;

    @Column(name = "earned_rate_date")
    private LocalDate earnedRateDate;

    // ─── Payout tracking ───────────────────────────────────────────────────

    @Column(name = "payout_reference", length = 120)
    private String payoutReference;

    @Column(name = "paid_at")
    private Instant paidAt;

    /**
     * Rate to the organization's official currency vigente at {@link #paidAt}
     * (period-close settlement). Compared against {@link #exchangeRateAtEarned}
     * to surface the currency-difference the company absorbs when a monthly
     * commission is devengada on one date and disbursed on another with the
     * rate having moved in between. Set by {@code CommissionPayoutService}
     * when the row transitions to {@code PAID}; {@code null} until then.
     */
    @Column(name = "exchange_rate_at_paid", precision = 18, scale = 8)
    private BigDecimal exchangeRateAtPaid;

    @Column(name = "paid_rate_date")
    private LocalDate paidRateDate;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "void_reason", columnDefinition = "text")
    private String voidReason;

    @Column(name = "admin_notes", columnDefinition = "text")
    private String adminNotes;

    // ─── Commercial approval (V107, hub plan §4) ───────────────────────────

    /**
     * Who reviewed this commission — set for both {@code APPROVED} and
     * {@code REJECTED} (the outcome differs, but "who/when reviewed it"
     * doesn't need two separate column pairs).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private User approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    /** Required only when {@link #getStatus()} is {@code REJECTED} (V107 CHECK). */
    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    // ─── Inner enums (V26 CHECK constraint values) ─────────────────────────

    public enum AppliesTo {
        INSCRIPTION, MONTHLY
    }

    public enum PeriodStrategy {
        DAILY, WEEKLY, BIWEEKLY, MONTHLY, QUARTERLY, SEMIANNUAL, ANNUAL
    }

    /**
     * Workflow values pinned by the V26/V107 CHECK on {@link
     * BaseEntity#getStatus()}. {@code APPROVED}/{@code REJECTED} (V107) sit
     * between {@code PENDING} (freshly calculated, a simulation) and {@code
     * PAID} — only a direct commission carries this state; hierarchy
     * overrides and retroactive top-ups inherit it by cascade (see {@code
     * CommissionApprovalService}), never approved independently.
     */
    public enum CommissionStatus {
        PENDING, APPROVED, REJECTED, PAID, VOIDED, DISPUTED
    }
}

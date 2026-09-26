package com.fenixcore.optibienestar360.modules.promoter.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
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
 * A promoter's final right to a prize for one {@link CompetitiveCommissionRule} position, in one
 * evaluated period (hub plan competitive-commission-rules, Fase 2b). What's actually been paid
 * toward it lives in {@link CompetitiveCommissionAwardSettlement} — this row is the entitlement,
 * not the ledger.
 *
 * <p>Lifecycle: {@code PROVISIONAL} (still subject to reconciliation — a better-ranked promoter
 * could still displace this one before the period closes) → {@code PENDING} (frozen once the
 * period closes and {@code confirmationDelayDays} elapses, or immediately for FIRST_TO_REACH once
 * confirmed) → {@code PAID}/{@code VOIDED}. {@code selectionSource}/{@code manualDecisionId} are
 * D16 (Fase 2c) — always {@code AUTO}/{@code null} until then.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "competitive_commission_awards")
@AttributeOverride(name = "id", column = @Column(name = "competitive_commission_awards_id", nullable = false, updatable = false))
public class CompetitiveCommissionAward extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "competitive_commission_rule_id", nullable = false)
    private CompetitiveCommissionRule rule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "competitive_commission_rule_position_id", nullable = false)
    private CompetitiveCommissionRulePosition position;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promoter_id", nullable = false)
    private Promoter promoter;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "award_position", nullable = false)
    private int awardPosition;

    @Column(name = "tie_group_size", nullable = false)
    private short tieGroupSize = 1;

    @Column(name = "metric_value", precision = 14, scale = 2, nullable = false)
    private BigDecimal metricValue;

    @Column(name = "metric_transaction_count", nullable = false)
    private int metricTransactionCount;

    @Column(name = "achieved_at")
    private Instant achievedAt;

    @Column(name = "awarded_at", nullable = false)
    private Instant awardedAt = Instant.now();

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "reward_type", length = 20, nullable = false)
    private CompetitiveCommissionRulePosition.RewardType rewardType;

    @Column(name = "flat_amount", precision = 10, scale = 2)
    private BigDecimal flatAmount;

    @Column(name = "reward_pct", precision = 5, scale = 2)
    private BigDecimal rewardPct;

    /** PERCENTAGE only — the metric/basis amount the percentage was applied to. */
    @Column(name = "basis_amount", precision = 14, scale = 2)
    private BigDecimal basisAmount;

    /** The award's total entitlement — settlements accumulate toward this, never past it. */
    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @Column(name = "exchange_rate_at_award", precision = 18, scale = 8)
    private BigDecimal exchangeRateAtAward;

    @Column(name = "award_rate_date")
    private LocalDate awardRateDate;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "payout_reference", length = 120)
    private String payoutReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payout_payment_id")
    private Payment payoutPayment;

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

    /** Frozen at award time — the rule can change or be deleted afterward without corrupting history. */
    @Column(name = "rule_name_snapshot", length = 150, nullable = false)
    private String ruleNameSnapshot;

    /** Rule/position/scope/campaign/window/date-basis/tie-policy at award time — audit + debugging. */
    @Column(name = "snapshot_json", columnDefinition = "jsonb", nullable = false)
    private String snapshotJson;

    /** D16 (Fase 2c) — always {@code AUTO} until manual decisions exist. */
    @Enumerated(EnumType.STRING)
    @Column(name = "selection_source", length = 10, nullable = false)
    private SelectionSource selectionSource = SelectionSource.AUTO;

    /** D16 (Fase 2c) — no FK yet (the manual-decisions table doesn't exist until then). */
    @Column(name = "manual_decision_id")
    private Long manualDecisionId;

    public enum SelectionSource {
        AUTO, MANUAL
    }

    public enum AwardStatus {
        PROVISIONAL, PENDING, PAID, VOIDED
    }
}

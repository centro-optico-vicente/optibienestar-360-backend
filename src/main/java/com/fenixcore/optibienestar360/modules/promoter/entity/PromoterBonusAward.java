package com.fenixcore.optibienestar360.modules.promoter.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.RewardType;
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
 * A granted bonus (V37) — one row per (rule, promoter, window) award. The ledger
 * that makes the engine auditable and idempotent: for PER_BLOCK rules the row
 * records how many blocks it granted, so a re-evaluation of the same window (or
 * of a LIFETIME rule) only grants the positive delta and never double-pays.
 *
 * <p>The reward is snapshotted inline ({@link #rewardType}, {@link #flatAmount} /
 * {@link #rewardPct}, {@link #basisAmount}, {@link #amount}) so a later edit of
 * the parent rule never rewrites award history — same policy as
 * {@link Commission}'s tier snapshot.</p>
 *
 * <p>{@code status} (inherited from {@link BaseEntity}) follows the payout
 * lifecycle PENDING → PAID / VOIDED, pinned by the V37 CHECK.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "promoter_bonus_awards")
@AttributeOverride(name = "id", column = @Column(name = "promoter_bonus_awards_id", nullable = false, updatable = false))
public class PromoterBonusAward extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bonus_rule_id", nullable = false)
    private CommissionBonusRule rule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promoter_id", nullable = false)
    private Promoter promoter;

    @Column(name = "window_start", nullable = false)
    private LocalDate windowStart;

    @Column(name = "window_end", nullable = false)
    private LocalDate windowEnd;

    @Column(name = "blocks_awarded", nullable = false)
    private int blocksAwarded;

    @Column(name = "metric_count", nullable = false)
    private int metricCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "reward_type", nullable = false, length = 20)
    private RewardType rewardType;

    @Column(name = "flat_amount", precision = 10, scale = 2)
    private BigDecimal flatAmount;

    @Column(name = "reward_pct", precision = 5, scale = 2)
    private BigDecimal rewardPct;

    @Column(name = "basis_amount", precision = 12, scale = 2)
    private BigDecimal basisAmount;

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reward_currency_id", nullable = false)
    private Currency rewardCurrency;

    @Column(name = "rule_name_snapshot", nullable = false, length = 150)
    private String ruleNameSnapshot;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    // ─── Payout lifecycle ────────────────────────────────────────────────────

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

    /** Values for {@link BaseEntity#getStatus()} pinned by the V37 CHECK. */
    public enum AwardStatus {
        PENDING, PAID, VOIDED
    }
}

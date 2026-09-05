package com.fenixcore.optibienestar360.modules.promoter.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;
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
 * Ledger of a prize granted to a promoter for placing on the leaderboard in a
 * closed period (v2 PDF #5, V42). Idempotent per (promoter, period, rank) via the
 * V42 partial UNIQUE — the period-close runner re-runs safely. Prize + ranking
 * snapshot inline so later config edits never rewrite history.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "leaderboard_prize_awards")
@AttributeOverride(name = "id", column = @Column(name = "leaderboard_prize_awards_id", nullable = false, updatable = false))
public class LeaderboardPrizeAward extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promoter_id", nullable = false)
    private Promoter promoter;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_strategy", length = 20, nullable = false)
    private PeriodStrategy periodStrategy;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "rank", nullable = false)
    private int rank;

    /** The commission total that ranked the promoter (snapshot for audit). */
    @Column(name = "metric_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal metricAmount;

    @Column(name = "prize_amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal prizeAmount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prize_currency_id", nullable = false)
    private Currency prizeCurrency;

    @Column(name = "awarded_at", nullable = false)
    private Instant awardedAt = Instant.now();

    // ─── Payout lifecycle (V92) ───────────────────────────────────────────────

    /** When the award was actually paid out — distinct from {@link #awardedAt} (period-close). Null until PAID. */
    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "payout_reference", length = 120)
    private String payoutReference;

    /** Units of the organization's official currency per 1 unit of {@link #prizeCurrency}, as of {@link #paidAt}. Null when never paid, or paid without a conversion (same currency). */
    @Column(name = "exchange_rate_used", precision = 18, scale = 8)
    private BigDecimal exchangeRateUsed;

    @Column(name = "exchange_rate_date")
    private LocalDate exchangeRateDate;

    /** Workflow values pinned by convention (mirrors PromoterBonusAward). */
    public enum AwardStatus {
        PENDING, PAID, VOIDED
    }
}

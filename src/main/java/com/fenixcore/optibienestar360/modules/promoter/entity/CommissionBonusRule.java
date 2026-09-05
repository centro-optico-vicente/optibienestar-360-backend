package com.fenixcore.optibienestar360.modules.promoter.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.catalog.entity.PromoterType;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
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
import java.time.LocalDate;

/**
 * A configurable bonus/award rule (V37, v2 PDF #5 "Premiaciones"). Rewards a
 * promoter for crossing a subscriber-count goal in a window — distinct from the
 * per-payment {@link Commission} ledger.
 *
 * <p>The rule is four independent knobs, so the three shapes the business asked
 * for all fall out of the same table:</p>
 * <table>
 *   <tr><th>Ask</th><th>metric</th><th>accrual</th><th>window</th><th>reward</th></tr>
 *   <tr><td>every 500 new → $100</td><td>NEW_SUBSCRIBERS</td><td>PER_BLOCK</td><td>LIFETIME</td><td>FLAT 100</td></tr>
 *   <tr><td>300 active/month → $50</td><td>ACTIVE_SUBSCRIBERS</td><td>THRESHOLD</td><td>MONTHLY</td><td>FLAT 50</td></tr>
 *   <tr><td>campaign, per 50 new → $200</td><td>NEW_SUBSCRIBERS</td><td>PER_BLOCK</td><td>CAMPAIGN</td><td>FLAT 200</td></tr>
 * </table>
 *
 * <p>Rules are combinable (many active at once) and a promoter may satisfy
 * several in the same period; each is evaluated independently. {@code is_active}
 * (BaseEntity) is the enable/disable switch the evaluator filters on. The V37
 * CHECK constraints pin the enum columns + reward/campaign coherence.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "commission_bonus_rules")
@AttributeOverride(name = "id", column = @Column(name = "commission_bonus_rules_id", nullable = false, updatable = false))
public class CommissionBonusRule extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BonusMetric metric;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccrualMode accrual;

    @Column(name = "threshold_count", nullable = false)
    private int thresholdCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "window_strategy", nullable = false, length = 20)
    private WindowStrategy windowStrategy;

    @Column(name = "campaign_start")
    private LocalDate campaignStart;

    @Column(name = "campaign_end")
    private LocalDate campaignEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "reward_type", nullable = false, length = 20)
    private RewardType rewardType;

    @Column(name = "flat_amount", precision = 10, scale = 2)
    private BigDecimal flatAmount;

    @Column(name = "reward_pct", precision = 5, scale = 2)
    private BigDecimal rewardPct;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reward_currency_id", nullable = false)
    private Currency rewardCurrency;

    @Column(name = "include_system_promoters", nullable = false)
    private boolean includeSystemPromoters = false;

    /** Optional promoter-type scope. {@code null} = applies to every promoter type. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promoter_type_id")
    private PromoterType promoterType;

    /** What we count per promoter in the window. */
    public enum BonusMetric {
        /** Members enrolled to the promoter within the window (by {@code enrolled_at}). */
        NEW_SUBSCRIBERS,
        /** Members of the promoter with an ACTIVE membership as of evaluation. */
        ACTIVE_SUBSCRIBERS
    }

    /** How the observed count turns into award units. */
    public enum AccrualMode {
        /** One award unit per full block of {@code threshold_count}; repeats. */
        PER_BLOCK,
        /** A single award once the count reaches {@code threshold_count}. */
        THRESHOLD
    }

    /** Evaluation window. LIFETIME = cumulative; CAMPAIGN = fixed date range. */
    public enum WindowStrategy {
        LIFETIME, DAILY, WEEKLY, BIWEEKLY, MONTHLY, QUARTERLY, SEMIANNUAL, ANNUAL, CAMPAIGN
    }

    /** Money or percentage. Exactly one of {@code flatAmount} / {@code rewardPct} is set. */
    public enum RewardType {
        FLAT, PERCENTAGE
    }
}

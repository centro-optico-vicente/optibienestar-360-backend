package com.fenixcore.optibienestar360.modules.promoter.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.catalog.entity.PromoterType;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

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

    /** Only meaningful when {@link #metric} is a count metric (NEW/ACTIVE_SUBSCRIBERS). */
    @Column(name = "threshold_count", nullable = false)
    private int thresholdCount;

    /**
     * Only meaningful when {@link #metric} is {@link BonusMetric#AMOUNT_COLLECTED}
     * (I-BE, hub plan Part I) — minimum amount collected in the window, compared
     * against the sum of the promoter's payments converted to {@link #thresholdCurrency}.
     */
    @Column(name = "threshold_amount", precision = 14, scale = 2)
    private BigDecimal thresholdAmount;

    /** Currency {@link #thresholdAmount} is compared in — independent from {@link #rewardCurrency}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "threshold_currency_id")
    private Currency thresholdCurrency;

    /**
     * Accumulation window — how the observed metric count/amount is measured
     * (e.g. "reached 500 new subscribers within the MONTHLY window"). Renamed
     * from {@code windowStrategy}/{@code window_strategy} (Fase A, hub plan
     * commission-frequency-currency-unification) to line up with the same
     * axis name on {@link CommissionTier#getAccrualPeriodStrategy()} /
     * {@link HierarchyOverrideTier#getAccrualPeriodStrategy()} /
     * {@link CollectionCommissionTier#getAccrualPeriodStrategy()}. The only
     * axis where {@link WindowStrategy#CAMPAIGN} and {@link
     * WindowStrategy#LIFETIME} are valid — the 3 settlement axes below are
     * always periodic (DB CHECK excludes both).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "accrual_period_strategy", nullable = false, length = 20)
    private WindowStrategy accrualPeriodStrategy;

    /**
     * How often a partial disbursement of an already-accrued award is paid
     * out (Fase A) — independent from {@link #accrualPeriodStrategy}, same
     * spirit as {@code CommissionTier.partialSettlementPeriodStrategy} (V112).
     * {@link WindowStrategy#CAMPAIGN}/{@link WindowStrategy#LIFETIME} are not
     * valid here (DB CHECK) — only {@link #accrualPeriodStrategy} can anchor
     * a rule to a campaign or run lifetime-cumulative.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "partial_settlement_period_strategy", nullable = false, length = 20)
    private WindowStrategy partialSettlementPeriodStrategy;

    /**
     * The containing window whose close triggers the final settlement of the
     * award (Fase A) — same spirit as {@code CommissionTier
     * .finalSettlementPeriodStrategy} (V112, renamed from {@code
     * settlementPeriodStrategy}). {@link WindowStrategy#CAMPAIGN}/{@link
     * WindowStrategy#LIFETIME} excluded (DB CHECK) — see {@link
     * #accrualPeriodStrategy}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "final_settlement_period_strategy", nullable = false, length = 20)
    private WindowStrategy finalSettlementPeriodStrategy;

    /**
     * The window whose close triggers a retroactive catch-up settlement of
     * this award (Fase A, new axis — no prior equivalent field). Default
     * {@code MONTHLY} (migration V145) reproduces today's behavior: retroactive
     * catch-up piggybacks on the same cadence as final settlement. {@link
     * WindowStrategy#CAMPAIGN}/{@link WindowStrategy#LIFETIME} excluded (DB
     * CHECK) — see {@link #accrualPeriodStrategy}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "retroactive_settlement_period_strategy", nullable = false, length = 20)
    private WindowStrategy retroactiveSettlementPeriodStrategy;

    /**
     * Day-of-week (1-7, WEEKLY/BIWEEKLY) or day-of-month (1-31, MONTHLY+)
     * anchor for {@link #accrualPeriodStrategy} (Fase A) — {@code null} lets
     * the evaluator fall back to its own default. Interpreted by {@code
     * PeriodStrategies} (phase 2, not touched here).
     */
    @Column(name = "accrual_period_anchor")
    private Short accrualPeriodAnchor;

    /** Same anchor semantics as {@link #accrualPeriodAnchor}, for {@link #partialSettlementPeriodStrategy}. */
    @Column(name = "partial_settlement_period_anchor")
    private Short partialSettlementPeriodAnchor;

    /** Same anchor semantics as {@link #accrualPeriodAnchor}, for {@link #finalSettlementPeriodStrategy}. */
    @Column(name = "final_settlement_period_anchor")
    private Short finalSettlementPeriodAnchor;

    /** Same anchor semantics as {@link #accrualPeriodAnchor}, for {@link #retroactiveSettlementPeriodStrategy}. */
    @Column(name = "retroactive_settlement_period_anchor")
    private Short retroactiveSettlementPeriodAnchor;

    /** Migrated V120 from {@code date} to {@code timestamptz} (existing rows moved to midnight UTC). */
    @Column(name = "campaign_start")
    private OffsetDateTime campaignStart;

    @Column(name = "campaign_end")
    private OffsetDateTime campaignEnd;

    /** Formal campaign anchor (V120) — {@code null} = a standing rule not tied to a {@link Campaign}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    /** Own validity window, usually copied from {@link #campaign} when associated (distinct from the legacy {@link #campaignStart}/{@link #campaignEnd} pair, which drive {@link WindowStrategy#CAMPAIGN} evaluation). */
    @Column(name = "starts_at")
    private OffsetDateTime startsAt;

    @Column(name = "ends_at")
    private OffsetDateTime endsAt;

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

    /**
     * Optional promoter-type scope (M:N, V137, hub plan Part F) — empty set
     * = applies to every promoter type, same semantics the single-FK
     * {@code promoter_type_id} carried before.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "commission_bonus_rule_promoter_types",
            joinColumns = @JoinColumn(name = "commission_bonus_rule_id"),
            inverseJoinColumns = @JoinColumn(name = "promoter_type_id"))
    private Set<PromoterType> promoterTypes = new HashSet<>();

    /** What we count per promoter in the window. */
    public enum BonusMetric {
        /** Members enrolled to the promoter within the window (by {@code enrolled_at}). */
        NEW_SUBSCRIBERS,
        /** Members of the promoter with an ACTIVE membership as of evaluation. */
        ACTIVE_SUBSCRIBERS,
        /**
         * Total amount collected (payments) by the promoter within the window,
         * converted to {@link #thresholdCurrency} and compared against
         * {@link #thresholdAmount} (I-BE, hub plan Part I).
         */
        AMOUNT_COLLECTED
    }

    /** How the observed count turns into award units. */
    public enum AccrualMode {
        /** One award unit per full block of {@code threshold_count}; repeats. */
        PER_BLOCK,
        /** A single award once the count reaches {@code threshold_count}. */
        THRESHOLD
    }

    /**
     * Evaluation window. LIFETIME = cumulative; CAMPAIGN = fixed date range.
     * Shared Java type across all 4 period axes ({@link #accrualPeriodStrategy}
     * and the 3 settlement axes) — LIFETIME/CAMPAIGN are only ever persisted
     * on {@link #accrualPeriodStrategy} (DB CHECK on the other 3 columns).
     */
    public enum WindowStrategy {
        LIFETIME, DAILY, WEEKLY, BIWEEKLY, MONTHLY, QUARTERLY, SEMIANNUAL, ANNUAL, CAMPAIGN
    }

    /** Money or percentage. Exactly one of {@code flatAmount} / {@code rewardPct} is set. */
    public enum RewardType {
        FLAT, PERCENTAGE
    }
}

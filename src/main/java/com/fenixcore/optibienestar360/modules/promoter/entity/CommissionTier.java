package com.fenixcore.optibienestar360.modules.promoter.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.catalog.entity.PromoterType;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan.PlanType;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;
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
 * Configurable commission tier (v2 PDF #5, V42) — the DB-driven replacement of
 * the hardcoded plan-type switch that {@code CommissionService} used in v1.
 *
 * <p>A tier qualifies for a promoter when their <b>new-subscriber count</b>
 * within the tier's {@link #periodStrategy} window reaches {@link #thresholdCount}
 * ({@code 0} = base tier, always qualifies). The engine applies the highest
 * qualifying tier among those matching the payment's plan and fee type.</p>
 *
 * <ul>
 *   <li>{@link #planType} — optional scope; {@code null} = applies to every plan
 *       type. The v1 per-plan rates ship as plan-scoped base tiers (threshold 0).</li>
 *   <li>{@link #commissionPct} XOR {@link #flatAmount} — exactly one (V42 CHECK),
 *       same shape as the {@link Commission} snapshot.</li>
 *   <li>{@link #appliesTo} — INSCRIPTION / MONTHLY / BOTH (the payment's fee type
 *       must match, or the tier must be BOTH).</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "commission_tiers")
@AttributeOverride(name = "id", column = @Column(name = "commission_tiers_id", nullable = false, updatable = false))
public class CommissionTier extends BaseEntity {

    @Column(name = "name", length = 80, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    /** Optional plan scope. {@code null} = applies to every plan type. */
    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", length = 20)
    private PlanType planType;

    /**
     * Optional promoter-type scope (M:N, V137, hub plan Part F) — empty set
     * = applies to every promoter type, same semantics the single-FK
     * {@code promoter_type_id} carried before.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "commission_tier_promoter_types",
            joinColumns = @JoinColumn(name = "commission_tier_id"),
            inverseJoinColumns = @JoinColumn(name = "promoter_type_id"))
    private Set<PromoterType> promoterTypes = new HashSet<>();

    @Column(name = "threshold_count", nullable = false)
    private int thresholdCount = 0;

    @Column(name = "commission_pct", precision = 5, scale = 2)
    private BigDecimal commissionPct;

    @Column(name = "flat_amount", precision = 10, scale = 2)
    private BigDecimal flatAmount;

    /** Only populated alongside {@link #flatAmount} (V120 CHECK, XOR with {@link #commissionPct}). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flat_amount_currency_id")
    private Currency flatAmountCurrency;

    /** Optional campaign anchor (V120) — {@code null} = a standing (non-campaign) tier. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    /** Own validity window, usually copied from {@link #campaign} when associated. */
    @Column(name = "starts_at")
    private OffsetDateTime startsAt;

    @Column(name = "ends_at")
    private OffsetDateTime endsAt;

    /**
     * Accumulation window — sizes the volume window {@link #thresholdCount}
     * (or {@link #thresholdAmount}, when {@link #basis} is {@code AMOUNT})
     * is measured against. Renamed from {@code periodStrategy} (Fase A, hub
     * plan commission-frequency-currency-unification) to line up with the
     * same axis name on {@link CommissionBonusRule#getAccrualPeriodStrategy()}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "accrual_period_strategy", length = 20, nullable = false)
    private PeriodStrategy accrualPeriodStrategy = PeriodStrategy.MONTHLY;

    /**
     * How often a partial cut of this rule is disbursed (V112, hub plan §3,
     * renamed from {@code payoutPeriodStrategy} in Fase A) — independent
     * from {@link #accrualPeriodStrategy}, which only sizes the volume
     * window {@link #thresholdCount} is measured against. Consumed by {@code
     * CommissionPeriodicSettlementService} together with {@link
     * #finalSettlementPeriodStrategy} via {@code PeriodCutCalculator}.
     * Default {@code MONTHLY} (same as {@link #finalSettlementPeriodStrategy})
     * yields a single cut equal to the whole settlement window — today's
     * behavior.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "partial_settlement_period_strategy", length = 20, nullable = false)
    private PeriodStrategy partialSettlementPeriodStrategy = PeriodStrategy.MONTHLY;

    /**
     * The containing window whose close triggers the top-up to the final
     * highest-qualifying band (V112, hub plan §3, renamed from {@code
     * settlementPeriodStrategy} in Fase A) — the {@code settlementStart}/
     * {@code settlementEnd} bounds passed to {@code
     * PeriodCutCalculator.cuts}/{@code cutContaining}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "final_settlement_period_strategy", length = 20, nullable = false)
    private PeriodStrategy finalSettlementPeriodStrategy = PeriodStrategy.MONTHLY;

    /**
     * The window whose close triggers a retroactive catch-up settlement of
     * this tier (Fase A, new axis). Default {@code MONTHLY}, backfilled from
     * {@link #finalSettlementPeriodStrategy} (migration V146) to reproduce
     * today's behavior: the retroactive top-up already fires together with
     * final settlement.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "retroactive_settlement_period_strategy", length = 20, nullable = false)
    private PeriodStrategy retroactiveSettlementPeriodStrategy = PeriodStrategy.MONTHLY;

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

    /**
     * Which field a tier's qualification is keyed on (Fase A, new axis).
     * {@code COUNT} (default, backward compatible) keeps today's behavior —
     * {@link #thresholdCount} new-subscriber volume. {@code AMOUNT} switches
     * qualification to {@link #thresholdAmount} (converted to {@link
     * #thresholdAmountCurrency} by the evaluator — phase 2, not built here).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "basis", length = 10, nullable = false)
    private BasisType basis = BasisType.COUNT;

    /** Only meaningful when {@link #basis} is {@code AMOUNT} — minimum collected-amount threshold, {@code >=} semantics (same as {@link #thresholdCount}). */
    @Column(name = "threshold_amount", precision = 14, scale = 2)
    private BigDecimal thresholdAmount;

    /** Reference currency for {@link #thresholdAmount} — required when {@link #basis} is {@code AMOUNT}, same pattern as {@link #flatAmountCurrency}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "threshold_amount_currency_id")
    private Currency thresholdAmountCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "applies_to", length = 20, nullable = false)
    private AppliesTo appliesTo = AppliesTo.BOTH;

    /** Which fee type the tier applies to. {@code BOTH} matches inscription and monthly. */
    public enum AppliesTo {
        INSCRIPTION, MONTHLY, BOTH
    }

    /** Which field ({@link #thresholdCount} or {@link #thresholdAmount}) a tier's qualification is keyed on (Fase A). */
    public enum BasisType {
        COUNT, AMOUNT
    }
}

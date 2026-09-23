package com.fenixcore.optibienestar360.modules.promoter.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
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
import java.time.OffsetDateTime;

/**
 * Configurable hierarchy-override band (V102) — same shape/spirit as {@link
 * CommissionTier}, but scoped by {@code (rank, category)} instead of
 * {@code (planType, promoterType)}: every {@link PromoterRank} can have a
 * completely independent band table per {@link OverrideCategory}, and
 * {@code INSCRIPTION} bands never share thresholds with {@code COLLECTION}
 * bands.
 *
 * <p>{@link #thresholdCount} measures the beneficiary's <b>team</b> volume
 * (the whole subtree under them, via {@code PromoterHierarchyService
 * .resolveTeamMemberIds}) — never their own personal sales. {@code 0} = base
 * band, always qualifies.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "hierarchy_override_tiers")
@AttributeOverride(name = "id", column = @Column(name = "hierarchy_override_tiers_id", nullable = false, updatable = false))
public class HierarchyOverrideTier extends BaseEntity {

    @Column(name = "name", length = 80, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rank_id", nullable = false)
    private PromoterRank rank;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 20, nullable = false)
    private OverrideCategory category;

    @Column(name = "threshold_count", nullable = false)
    private int thresholdCount = 0;

    @Column(name = "override_pct", precision = 5, scale = 2)
    private BigDecimal overridePct;

    @Column(name = "flat_amount", precision = 10, scale = 2)
    private BigDecimal flatAmount;

    /** Only populated alongside {@link #flatAmount} — mirrors the {@code commissions.currency} pattern (ADR 0015). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flat_amount_currency_id")
    private Currency flatAmountCurrency;

    /** Optional campaign anchor (V120) — {@code null} = a standing band. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    @Column(name = "starts_at")
    private OffsetDateTime startsAt;

    @Column(name = "ends_at")
    private OffsetDateTime endsAt;

    /**
     * Accumulation window — sizes the team-volume window {@link #thresholdCount}
     * (or {@link #thresholdAmount}, when {@link #basis} is {@code AMOUNT})
     * is measured against. Renamed from {@code periodStrategy} (Fase A, hub
     * plan commission-frequency-currency-unification) to line up with {@link
     * CommissionTier#getAccrualPeriodStrategy()}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "accrual_period_strategy", length = 20, nullable = false)
    private Commission.PeriodStrategy accrualPeriodStrategy = Commission.PeriodStrategy.MONTHLY;

    /**
     * How often a partial cut of this band is disbursed (V112, hub plan §3,
     * renamed from {@code payoutPeriodStrategy} in Fase A) — same independent
     * axis as {@link CommissionTier#getPartialSettlementPeriodStrategy()}.
     * Consumed by {@code HierarchyOverridePeriodicSettlementService}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "partial_settlement_period_strategy", length = 20, nullable = false)
    private Commission.PeriodStrategy partialSettlementPeriodStrategy = Commission.PeriodStrategy.MONTHLY;

    /**
     * The containing window whose close triggers the top-up to the final
     * highest-qualifying band (V112, hub plan §3, renamed from {@code
     * settlementPeriodStrategy} in Fase A).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "final_settlement_period_strategy", length = 20, nullable = false)
    private Commission.PeriodStrategy finalSettlementPeriodStrategy = Commission.PeriodStrategy.MONTHLY;

    /**
     * The window whose close triggers a retroactive catch-up settlement of
     * this band (Fase A, new axis). Default {@code MONTHLY}, backfilled from
     * {@link #finalSettlementPeriodStrategy} (migration V147) to reproduce
     * today's behavior.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "retroactive_settlement_period_strategy", length = 20, nullable = false)
    private Commission.PeriodStrategy retroactiveSettlementPeriodStrategy = Commission.PeriodStrategy.MONTHLY;

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
     * Which field a band's qualification is keyed on (Fase A, new axis).
     * {@code COUNT} (default, backward compatible) keeps today's behavior —
     * {@link #thresholdCount} team volume. {@code AMOUNT} switches
     * qualification to {@link #thresholdAmount} (converted to {@link
     * #thresholdAmountCurrency} by the evaluator — phase 2, not built here).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "basis", length = 10, nullable = false)
    private BasisType basis = BasisType.COUNT;

    /** Only meaningful when {@link #basis} is {@code AMOUNT} — minimum team-collected-amount threshold, {@code >=} semantics (same as {@link #thresholdCount}). */
    @Column(name = "threshold_amount", precision = 14, scale = 2)
    private BigDecimal thresholdAmount;

    /** Reference currency for {@link #thresholdAmount} — required when {@link #basis} is {@code AMOUNT}, same pattern as {@link #flatAmountCurrency}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "threshold_amount_currency_id")
    private Currency thresholdAmountCurrency;

    /** The two override categories — apertura (INSCRIPTION) and cobranza (COLLECTION). */
    public enum OverrideCategory {
        INSCRIPTION, COLLECTION
    }

    /** Which field ({@link #thresholdCount} or {@link #thresholdAmount}) a band's qualification is keyed on (Fase A). */
    public enum BasisType {
        COUNT, AMOUNT
    }
}

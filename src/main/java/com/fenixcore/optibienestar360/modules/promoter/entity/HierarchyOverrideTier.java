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

    @Enumerated(EnumType.STRING)
    @Column(name = "period_strategy", length = 20, nullable = false)
    private Commission.PeriodStrategy periodStrategy = Commission.PeriodStrategy.MONTHLY;

    /**
     * How often a partial cut of this band is disbursed (V112, hub plan §3)
     * — same independent axis as {@link CommissionTier#getPayoutPeriodStrategy()}.
     * Consumed by {@code HierarchyOverridePeriodicSettlementService}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "payout_period_strategy", length = 20, nullable = false)
    private Commission.PeriodStrategy payoutPeriodStrategy = Commission.PeriodStrategy.MONTHLY;

    /**
     * The containing window whose close triggers the retroactive top-up to
     * the final highest-qualifying band (V112, hub plan §3).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_period_strategy", length = 20, nullable = false)
    private Commission.PeriodStrategy settlementPeriodStrategy = Commission.PeriodStrategy.MONTHLY;

    /** The two override categories — apertura (INSCRIPTION) and cobranza (COLLECTION). */
    public enum OverrideCategory {
        INSCRIPTION, COLLECTION
    }
}

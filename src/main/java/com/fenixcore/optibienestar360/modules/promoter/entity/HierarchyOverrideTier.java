package com.fenixcore.optibienestar360.modules.promoter.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "period_strategy", length = 20, nullable = false)
    private Commission.PeriodStrategy periodStrategy = Commission.PeriodStrategy.MONTHLY;

    /** The two override categories — apertura (INSCRIPTION) and cobranza (COLLECTION). */
    public enum OverrideCategory {
        INSCRIPTION, COLLECTION
    }
}

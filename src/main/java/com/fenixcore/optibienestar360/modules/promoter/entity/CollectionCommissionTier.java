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
 * Collection commission tier (ADR 0013 §3, V44, V126) — decreasing reward by
 * how late (days) or how large (amount) the recurring (MONTHLY) payment was.
 *
 * <p>{@link #basis} picks which bucket field is live: {@code DAYS} (default,
 * backward compatible) applies when the payment's days-to-collect is
 * {@code <= maxDays}; {@code AMOUNT} applies when the payment amount is
 * {@code <= maxAmount}. The engine picks the smallest qualifying bucket for
 * whichever basis the tier uses. Orthogonal to {@link CommissionTier}, which
 * scopes by monthly new-subscriber volume.</p>
 *
 * <p>Reward is pct XOR flat (V126), same shape as {@link HierarchyOverrideTier}.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "collection_commission_tiers")
@AttributeOverride(name = "id", column = @Column(name = "collection_commission_tiers_id", nullable = false, updatable = false))
public class CollectionCommissionTier extends BaseEntity {

    @Column(name = "name", length = 80, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "basis", length = 10, nullable = false)
    private Basis basis = Basis.DAYS;

    /** Only meaningful when {@link #basis} is {@code DAYS}. */
    @Column(name = "max_days")
    private Integer maxDays;

    /** Only meaningful when {@link #basis} is {@code AMOUNT}. */
    @Column(name = "max_amount", precision = 14, scale = 2)
    private BigDecimal maxAmount;

    @Column(name = "commission_pct", precision = 5, scale = 2)
    private BigDecimal commissionPct;

    @Column(name = "flat_amount", precision = 10, scale = 2)
    private BigDecimal flatAmount;

    /** Only populated alongside {@link #flatAmount} — mirrors the {@code commissions.currency} pattern (ADR 0015). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flat_amount_currency_id")
    private Currency flatAmountCurrency;

    /**
     * Optional promoter-type scope (M:N, V137, hub plan Part F) — empty set
     * = applies to every promoter type, same semantics the single-FK
     * {@code promoter_type_id} carried before.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "collection_commission_tier_promoter_types",
            joinColumns = @JoinColumn(name = "collection_commission_tier_id"),
            inverseJoinColumns = @JoinColumn(name = "promoter_type_id"))
    private Set<PromoterType> promoterTypes = new HashSet<>();

    /** Optional campaign anchor (V126) — {@code null} = a standing tier. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    @Column(name = "starts_at")
    private OffsetDateTime startsAt;

    @Column(name = "ends_at")
    private OffsetDateTime endsAt;

    /** Which bucket field ({@link #maxDays} or {@link #maxAmount}) the tier is keyed on. */
    public enum Basis {
        DAYS, AMOUNT
    }
}

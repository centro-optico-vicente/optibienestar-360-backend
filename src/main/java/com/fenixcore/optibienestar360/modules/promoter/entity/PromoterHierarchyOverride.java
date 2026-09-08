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
import java.time.Instant;
import java.time.LocalDate;

/**
 * Hierarchy-override cascade ledger (V102) — one row per source event, same
 * granular shape as {@link Commission}. Exactly one of {@link
 * #sourceCommission} / {@link #sourceOverride} is set (V102 CHECK): a
 * level-2 override (the earner's immediate supervisor) is born from a
 * {@link Commission}; a level-3+ override is born from the override the
 * level right below just earned — never from the original commission
 * directly, so {@code HierarchyOverrideService.cascadeFrom}'s recursion can
 * never skip a level (hub plan §2).
 *
 * <p>{@link #basisAmount} is what the immediate inferior earned (the source
 * commission's/override's own {@code amount}), never the gross sale/payment
 * amount — confirmed by the whiteboard numeric example (hub plan §Contexto).
 * {@link #currency} is inherited from the source without conversion, since
 * the source is already priced in a fixed currency (ADR 0015 integration
 * note in the hub plan).</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "promoter_hierarchy_overrides")
@AttributeOverride(name = "id", column = @Column(name = "promoter_hierarchy_overrides_id", nullable = false, updatable = false))
public class PromoterHierarchyOverride extends BaseEntity {

    /** The beneficiary — the Supervisor/Coordinador earning this row. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promoter_id", nullable = false)
    private Promoter promoter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_commission_id")
    private Commission sourceCommission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_override_id")
    private PromoterHierarchyOverride sourceOverride;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 20, nullable = false)
    private HierarchyOverrideTier.OverrideCategory category;

    @Column(name = "basis_amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal basisAmount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tier_id", nullable = false)
    private HierarchyOverrideTier tier;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_strategy", length = 20, nullable = false)
    private Commission.PeriodStrategy periodStrategy;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "earned_at", nullable = false)
    private Instant earnedAt = Instant.now();

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "void_reason", columnDefinition = "text")
    private String voidReason;

    /** Values for {@link BaseEntity#getStatus()} pinned by the V102 CHECK. */
    public enum OverrideStatus {
        PENDING, PAID, VOIDED
    }
}

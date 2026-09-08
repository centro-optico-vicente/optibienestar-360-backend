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
 * Generic "corte parcial + retroactivo al cierre" top-up ledger (V105, hub
 * plan ".ai/plans/2026-09-07-hierarchical-commissions-plan.md" §3). One row
 * per (beneficiary, {@link #ledgerType}, settlement period) — holds the
 * delta between what the settlement period's final highest-qualifying band
 * would have paid on the whole period's {@link #basisAmount} ({@link
 * #targetAmount}) and what was already {@code PAID} across that period's
 * partial cuts ({@link #alreadyPaidAmount}) before the settlement closed.
 *
 * <p>Never mutates the underlying {@link Commission}/{@link
 * PromoterHierarchyOverride} rows those cuts already paid — those stay
 * {@code PAID} and untouched, exactly as {@code CommissionReRatingService}/
 * {@code HierarchyOverrideReRatingService} already guarantee (they only
 * re-rate {@code PENDING} rows). This is the separate ledger row that
 * closes the gap instead.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "commission_retroactive_topups")
@AttributeOverride(name = "id", column = @Column(name = "commission_retroactive_topups_id", nullable = false, updatable = false))
public class CommissionRetroactiveTopUp extends BaseEntity {

    /** The beneficiary — a promoter (DIRECT_*) or a Supervisor/Coordinador (HIERARCHY_OVERRIDE_*). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promoter_id", nullable = false)
    private Promoter promoter;

    @Enumerated(EnumType.STRING)
    @Column(name = "ledger_type", length = 40, nullable = false)
    private LedgerType ledgerType;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    /** Sum of the whole settlement period's calculation basis (all cuts combined), the input to {@link #targetAmount}. */
    @Column(name = "basis_amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal basisAmount;

    /** What the final highest-qualifying band would have paid on {@link #basisAmount}. */
    @Column(name = "target_amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal targetAmount;

    /** Sum of what was already {@code PAID} across the period's partial cuts, before this top-up. */
    @Column(name = "already_paid_amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal alreadyPaidAmount;

    /** {@code targetAmount - alreadyPaidAmount} — always {@code > 0} (a row is only inserted when positive). */
    @Column(name = "retro_amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal retroAmount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    /**
     * Bare id of the winning tier ({@code commission_tiers} for DIRECT_*,
     * {@code hierarchy_override_tiers} for HIERARCHY_OVERRIDE_*) — no FK,
     * same deferred-closure convention {@link Commission#getCommissionTierId()}
     * already uses, since the two source tables differ by ledger type.
     */
    @Column(name = "tier_id")
    private Long tierId;

    @Column(name = "tier_name_snapshot", length = 80)
    private String tierNameSnapshot;

    @Column(name = "payout_reference", length = 120)
    private String payoutReference;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "void_reason", columnDefinition = "text")
    private String voidReason;

    /** Values pinned by the V105 CHECK on {@code ledger_type}. */
    public enum LedgerType {
        DIRECT_INSCRIPTION, DIRECT_COLLECTION,
        HIERARCHY_OVERRIDE_INSCRIPTION, HIERARCHY_OVERRIDE_COLLECTION
    }

    /** Values for {@link BaseEntity#getStatus()} pinned by the V105 CHECK. */
    public enum TopUpStatus {
        PENDING, PAID, VOIDED
    }
}

package com.fenixcore.optibienestar360.modules.promoter.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionRetroactiveTopUp.LedgerType;
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
 * Multi-cut retroactive top-up ledger (Fase A, hub plan
 * commission-frequency-currency-unification, phase "retroactive settlement
 * axis"). Successor of {@link CommissionRetroactiveTopUp} — that table stays
 * frozen as historical data (one row per whole settlement period, overwritten
 * on re-run) and is never written to again by new code.
 *
 * <p>This table instead supports <b>several retroactive cuts inside the same
 * accrual period</b> (e.g. one row per fortnightly "corte de retroactivo"
 * inside a monthly accrual window), driven by each rule's own {@code
 * retroactiveSettlementPeriodStrategy}/{@code retroactiveSettlementPeriodAnchor}
 * axis ({@link CommissionTier}, {@link HierarchyOverrideTier}, {@link
 * CommissionBonusRule}, {@link CollectionCommissionTier} all carry it).</p>
 *
 * <p>One row per {@code (promoter, ledgerType, accrualPeriodStart,
 * accrualPeriodEnd, cutSequence)} — {@link #cutSequence} is the 1-based
 * index of {@link #cutStart}/{@link #cutEnd} among the retroactive cuts
 * {@code PeriodCutCalculator.cuts} slices the accrual window into. {@link
 * #basisAmountCumulative}/{@link #targetAmountCumulative} are cumulative
 * <b>from the start of the accrual period</b> through {@link #cutEnd} — not
 * just this cut's own slice — and {@link #alreadyPaidAmount} nets out both
 * the base commission/override rows already {@code PAID} in that same span
 * AND any earlier cut's own {@link #retroAmount} already {@code PAID}, so a
 * later cut that reaches a higher band pays only the incremental gap, never
 * double-paying a lower cut's already-disbursed retroactive amount.</p>
 *
 * <p>Never mutates the underlying {@link Commission}/{@link
 * PromoterHierarchyOverride} rows, nor {@link CommissionRetroactiveTopUp} —
 * same non-mutation guarantee as that class's Javadoc.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "commission_retroactive_topup_cuts")
@AttributeOverride(name = "id", column = @Column(name = "commission_retroactive_topup_cuts_id", nullable = false, updatable = false))
public class CommissionRetroactiveTopUpCut extends BaseEntity {

    /** The beneficiary — a promoter (DIRECT_*) or a Supervisor/Coordinador (HIERARCHY_OVERRIDE_*). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promoter_id", nullable = false)
    private Promoter promoter;

    /** Reuses {@link CommissionRetroactiveTopUp}'s enum — same four ledgers, same CHECK domain. */
    @Enumerated(EnumType.STRING)
    @Column(name = "ledger_type", length = 40, nullable = false)
    private LedgerType ledgerType;

    /** Whole accrual window (the grouping period, e.g. the calendar month) — the volume/basis metric's denominator. */
    @Column(name = "accrual_period_start", nullable = false)
    private LocalDate accrualPeriodStart;

    @Column(name = "accrual_period_end", nullable = false)
    private LocalDate accrualPeriodEnd;

    /** 1-based order of {@link #cutStart}/{@link #cutEnd} among the accrual period's retroactive cuts. */
    @Column(name = "cut_sequence", nullable = false)
    private int cutSequence;

    /** This specific cut's own window (a slice of the accrual period). */
    @Column(name = "cut_start", nullable = false)
    private LocalDate cutStart;

    @Column(name = "cut_end", nullable = false)
    private LocalDate cutEnd;

    /** Cumulative qualifying basis from {@link #accrualPeriodStart} through {@link #cutEnd} (not just this cut's slice). */
    @Column(name = "basis_amount_cumulative", precision = 14, scale = 2, nullable = false)
    private BigDecimal basisAmountCumulative;

    /** What the highest band reached by {@link #basisAmountCumulative} would pay on the whole cumulative basis. */
    @Column(name = "target_amount_cumulative", precision = 14, scale = 2, nullable = false)
    private BigDecimal targetAmountCumulative;

    /** Base rows already {@code PAID} in the cumulative span + earlier cuts' {@link #retroAmount} already {@code PAID}. */
    @Column(name = "already_paid_amount", precision = 14, scale = 2, nullable = false)
    private BigDecimal alreadyPaidAmount;

    /** {@code targetAmountCumulative - alreadyPaidAmount} — always {@code > 0} (a row is only inserted when positive). */
    @Column(name = "retro_amount", precision = 14, scale = 2, nullable = false)
    private BigDecimal retroAmount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    /**
     * Bare id of the winning tier ({@code commission_tiers}/{@code
     * hierarchy_override_tiers}/{@code collection_commission_tiers} depending
     * on {@link #ledgerType}) — no FK, same deferred-closure convention as
     * {@link CommissionRetroactiveTopUp#getTierId()}.
     */
    @Column(name = "tier_id")
    private Long tierId;

    @Column(name = "tier_name_snapshot", length = 80)
    private String tierNameSnapshot;

    /** The OUT {@link Payment} that disbursed this cut's retroactive amount. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payout_payment_id")
    private Payment payoutPayment;

    @Column(name = "payout_reference", length = 120)
    private String payoutReference;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "void_reason", columnDefinition = "text")
    private String voidReason;

    /** Values for {@link BaseEntity#getStatus()} — same domain as {@link CommissionRetroactiveTopUp.TopUpStatus}. */
    public enum CutStatus {
        PENDING, PAID, VOIDED
    }
}

package com.fenixcore.optibienestar360.modules.membership.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * One billable month of a {@link Membership} (V153). Generated ahead of time
 * by the {@code MEMBERSHIP_CHARGE_GENERATION} daily job (or on demand when a
 * payment covers a period that has no row yet — see
 * {@code MembershipChargeService.ensureChargeForPeriod}), then settled by
 * {@link #coveredByPayment} once a payment applies to it.
 *
 * <p>Multi-month advances: a single {@link Payment} can cover several
 * consecutive {@code MembershipCharge} rows ({@link Payment#getAppliedPeriod()}
 * through {@link Payment#getCoverageThroughPeriod()}) — the commission engine
 * still calculates against the payment's full amount, so an advance is never
 * fractioned across the months it covers.</p>
 *
 * <p>{@code status} is inherited from {@link BaseEntity} as a {@code String};
 * service callers use {@link ChargeStatus} for type safety. The V153 CHECK
 * constraint pins the column to exactly these four values.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "membership_charges")
@AttributeOverride(name = "id", column = @Column(name = "membership_charges_id", nullable = false, updatable = false))
public class MembershipCharge extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membership_id", nullable = false)
    private Membership membership;

    /** First day of the covered month. */
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    /** Last day of the covered month. */
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    /**
     * Scheduled collection date for this period — same billing-cutover-day
     * projection {@code CommissionService.collectionDays} uses
     * ({@link Membership#getBillingStartDay()}, clamped to the period's
     * month length).
     */
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "covered_by_payment_id")
    private Payment coveredByPayment;

    /**
     * Lifecycle values that may land in {@link BaseEntity#getStatus()}. The
     * V153 CHECK constraint pins the column to exactly these four.
     */
    public enum ChargeStatus {
        PENDING, COVERED, OVERDUE, WAIVED
    }
}

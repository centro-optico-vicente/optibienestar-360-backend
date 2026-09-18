package com.fenixcore.optibienestar360.modules.payment.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.bank.entity.Bank;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
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
import java.time.Instant;

/**
 * A single method/amount/reference within a {@link Payment} (V117, hub plan
 * ".ai/plans/2026-09-17-payments-unification-plan.md"). Every payment today
 * still produces exactly one line, but the schema supports splitting one
 * payment across cash + transfer + any combination from day one.
 *
 * <p>{@code paymentType} here is the METHOD (FK {@link PaymentMethod}) — not
 * to be confused with {@link Payment#getPaymentType()}, which is the header's
 * REASON (FK {@link PaymentCategory}). Same field name on purpose (continuity
 * with the original single-catalog design), different target entity.</p>
 *
 * <p>Carries its own review state machine (same shape as {@link Payment}'s)
 * so a line can be approved/rejected independently of its siblings — today,
 * with one line per payment, {@code PaymentsService} keeps both in lockstep.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payment_lines")
@AttributeOverride(name = "id", column = @Column(name = "payment_line_id", nullable = false, updatable = false))
public class PaymentLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    /** METHOD — FK {@link PaymentMethod}, not {@link PaymentCategory}. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_type_id", nullable = false)
    private PaymentMethod paymentType;

    /** Only populated when {@code paymentType.mandatoryBankAccount} is true. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_id")
    private Bank bank;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @Column(name = "reference_number", length = 80)
    private String referenceNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_reason", columnDefinition = "text")
    private String reviewReason;
}

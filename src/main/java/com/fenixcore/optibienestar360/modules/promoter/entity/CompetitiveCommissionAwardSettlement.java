package com.fenixcore.optibienestar360.modules.promoter.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
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
 * One settlement cut (D14) toward a {@link CompetitiveCommissionAward}'s entitlement — the same
 * cumulative-netting ledger shape as {@code commission_retroactive_topup_cuts} (V150), keyed by
 * rule rather than just award because a {@code PARTIAL} cut can run before the award row even
 * exists (FIRST_TO_REACH not yet confirmed).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "competitive_commission_award_settlements")
@AttributeOverride(name = "id", column = @Column(name = "competitive_commission_award_settlements_id", nullable = false, updatable = false))
public class CompetitiveCommissionAwardSettlement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "competitive_commission_rule_id", nullable = false)
    private CompetitiveCommissionRule rule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "competitive_commission_award_id")
    private CompetitiveCommissionAward award;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promoter_id", nullable = false)
    private Promoter promoter;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "cut_kind", length = 20, nullable = false)
    private CutKind cutKind;

    @Column(name = "cut_sequence", nullable = false)
    private int cutSequence;

    @Column(name = "cut_start", nullable = false)
    private LocalDate cutStart;

    @Column(name = "cut_end", nullable = false)
    private LocalDate cutEnd;

    @Column(name = "award_position_at_cut")
    private Integer awardPositionAtCut;

    /** PERCENTAGE positions only. */
    @Column(name = "basis_amount_cumulative", precision = 14, scale = 2)
    private BigDecimal basisAmountCumulative;

    /** What's owed from {@code periodStart} through {@code cutEnd}, computed fresh each cut. */
    @Column(name = "entitlement_cumulative", precision = 12, scale = 2, nullable = false)
    private BigDecimal entitlementCumulative;

    /** Sum of PAID settlements of the same (rule, promoter, period) before this cut. */
    @Column(name = "already_paid_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal alreadyPaidAmount;

    /** {@code entitlementCumulative - alreadyPaidAmount} — only ever inserted when {@code > 0}. */
    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @Column(name = "exchange_rate_at_paid", precision = 18, scale = 8)
    private BigDecimal exchangeRateAtPaid;

    @Column(name = "paid_rate_date")
    private LocalDate paidRateDate;

    @Column(name = "payout_reference", length = 120)
    private String payoutReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payout_payment_id")
    private Payment payoutPayment;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "void_reason", columnDefinition = "text")
    private String voidReason;

    public enum CutKind {
        PARTIAL, RETROACTIVE, FINAL
    }

    public enum SettlementStatus {
        PENDING, PAID, VOIDED
    }
}

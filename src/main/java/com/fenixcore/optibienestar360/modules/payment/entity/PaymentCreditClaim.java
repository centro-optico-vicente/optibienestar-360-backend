package com.fenixcore.optibienestar360.modules.payment.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
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
 * A claim on the remaining credit of an approved payment (V205): the payer — or staff — says it is wrong,
 * overpaid or should be refunded, with the amount. While {@link ClaimStatus#OPEN} that amount is frozen
 * for automatic application; staff then resolve it (keep as credit, refund or reject).
 *
 * <p>The lifecycle value lives in {@link #claimStatus}; {@code BaseEntity.status} is the generic column and is not used.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payment_credit_claims")
@AttributeOverride(name = "id", column = @Column(name = "payment_credit_claims_id", nullable = false, updatable = false))
public class PaymentCreditClaim extends BaseEntity {

    public enum Reason { ERROR, OVERPAID, REFUND_REQUEST, OTHER }

    public enum ClaimStatus { OPEN, KEPT_AS_CREDIT, REFUNDED, REJECTED, WITHDRAWN, USED_BY_AGREEMENT }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(length = 20, nullable = false)
    private String reason;

    @Column(name = "claimed_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal claimedAmount;

    @Column(length = 500)
    private String comment;

    @Column(name = "claim_status", length = 20, nullable = false)
    private String claimStatus = ClaimStatus.OPEN.name();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opened_by")
    private User openedBy;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private User resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    @Column(name = "refunded_amount", precision = 12, scale = 2)
    private BigDecimal refundedAmount;
}

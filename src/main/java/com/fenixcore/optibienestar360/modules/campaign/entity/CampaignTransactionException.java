package com.fenixcore.optibienestar360.modules.campaign.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
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

/**
 * A manual admin override forcing a transaction (a {@link Payment} OR a
 * {@link Membership} — exactly one, DB CHECK constraint, V120) to count
 * ({@link ExceptionAction#INCLUDE}) or not count
 * ({@link ExceptionAction#EXCLUDE}) towards a {@link Campaign}, regardless of
 * what automatic scope/window resolution would otherwise decide.
 *
 * <p>{@link #getCreatedBy()} (inherited from {@link BaseEntity}) is who
 * recorded the exception — no separate {@code createdBy} column is added
 * here, it would duplicate the audited base column.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "campaign_transaction_exceptions")
@AttributeOverride(name = "id", column = @Column(name = "campaign_transaction_exceptions_id", nullable = false, updatable = false))
public class CampaignTransactionException extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_id")
    private Membership membership;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 10, nullable = false)
    private ExceptionAction action;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    public enum ExceptionAction {
        INCLUDE, EXCLUDE
    }
}

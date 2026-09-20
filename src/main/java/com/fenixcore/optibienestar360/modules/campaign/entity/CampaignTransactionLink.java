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

import java.time.OffsetDateTime;

/**
 * Records that a given transaction (a {@link Payment} OR a {@link Membership}
 * — exactly one, DB CHECK constraint, V120) counted towards a {@link Campaign}.
 * "Enrollment" in the feature spec maps to {@link Membership} — the concrete
 * subscription row created at enrollment time; there is no separate
 * {@code Enrollment} entity in this codebase.
 *
 * <p>{@link #source} distinguishes automatic resolution ({@code AUTO} — the
 * transaction naturally fell within the campaign's scope/window) from a
 * manual override recorded via {@link CampaignTransactionException}
 * ({@code EXCEPTION_INCLUDE}/{@code EXCEPTION_EXCLUDE}). An
 * {@code EXCEPTION_EXCLUDE} link is not expected to coexist with an
 * {@code AUTO} link for the same transaction — the resolver removes/replaces
 * the AUTO link when an exclusion exception is recorded.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "campaign_transaction_links")
@AttributeOverride(name = "id", column = @Column(name = "campaign_transaction_links_id", nullable = false, updatable = false))
public class CampaignTransactionLink extends BaseEntity {

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
    @Column(name = "source", length = 20, nullable = false)
    private LinkSource source = LinkSource.AUTO;

    @Column(name = "resolved_at", nullable = false)
    private OffsetDateTime resolvedAt;

    public enum LinkSource {
        AUTO, EXCEPTION_INCLUDE, EXCEPTION_EXCLUDE
    }
}

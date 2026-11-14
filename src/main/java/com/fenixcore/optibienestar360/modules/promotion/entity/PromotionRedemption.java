package com.fenixcore.optibienestar360.modules.promotion.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * The campaign slot a {@link MembershipPromotion} occupies (V257, hub ADR
 * 0038 §5): {@code PENDING → CONFIRMED}, or {@code EXPIRED} (deadline passed
 * without confirmation) / {@code RELEASED} (promotion lost before any
 * discount). PENDING and CONFIRMED rows occupy a slot of the campaign and of
 * the promotion; CONFIRMED rows are the confirmed count.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "promotion_redemptions")
@AttributeOverride(name = "id", column = @Column(name = "promotion_redemptions_id", nullable = false, updatable = false))
public class PromotionRedemption extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membership_promotion_id", nullable = false, unique = true)
    private MembershipPromotion membershipPromotion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promotion_id", nullable = false)
    private Promotion promotion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Enumerated(EnumType.STRING)
    @Column(name = "redemption_status", length = 20, nullable = false)
    private RedemptionStatus redemptionStatus;

    @Column(name = "reserved_at", nullable = false)
    private Instant reservedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    /** Deadline of a PENDING slot; {@code null} = never expires by itself (the campaign grace may still cut it). */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /** When the benefit of a CONFIRMED slot stops; {@code null} = no limit. */
    @Column(name = "usage_ends_at")
    private Instant usageEndsAt;

    /** When the slot was freed (EXPIRED or RELEASED). */
    @Column(name = "released_at")
    private Instant releasedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by_payment_id")
    private Payment confirmedByPayment;

    public boolean isPending() {
        return redemptionStatus == RedemptionStatus.PENDING;
    }

    public boolean isConfirmed() {
        return redemptionStatus == RedemptionStatus.CONFIRMED;
    }

    public enum RedemptionStatus {
        PENDING, CONFIRMED, EXPIRED, RELEASED
    }
}

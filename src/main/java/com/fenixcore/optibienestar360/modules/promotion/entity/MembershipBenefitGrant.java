package com.fenixcore.optibienestar360.modules.promotion.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyProduct;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.BenefitType;
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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The voucher a member receives when the slot of an ALLY_BENEFIT promotion is
 * CONFIRMED (V257, hub ADR 0038 §7). It snapshots the benefit of the
 * promotion and is redeemed by the ally when it registers the usage
 * ({@code benefit_usages.benefit_grant_id}): {@code AVAILABLE → REDEEMED} once
 * every unit is used, or {@code EXPIRED} / {@code CANCELED}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "membership_benefit_grants")
@AttributeOverride(name = "id", column = @Column(name = "membership_benefit_grants_id", nullable = false, updatable = false))
public class MembershipBenefitGrant extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membership_promotion_id", nullable = false, unique = true)
    private MembershipPromotion membershipPromotion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membership_id", nullable = false)
    private Membership membership;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promotion_id", nullable = false)
    private Promotion promotion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ally_id", nullable = false)
    private Ally ally;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ally_product_id", nullable = false)
    private AllyProduct allyProduct;

    @Enumerated(EnumType.STRING)
    @Column(name = "benefit_type", length = 20, nullable = false)
    private BenefitType benefitType;

    @Column(name = "benefit_pct", precision = 5, scale = 2)
    private BigDecimal benefitPct;

    @Column(name = "benefit_amount", precision = 18, scale = 2)
    private BigDecimal benefitAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "benefit_currency_id")
    private Currency benefitCurrency;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "remaining_qty", nullable = false)
    private int remainingQty;

    @Enumerated(EnumType.STRING)
    @Column(name = "grant_status", length = 20, nullable = false)
    private GrantStatus grantStatus;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    /** = the slot's usage end; {@code null} = no limit. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "redeemed_at")
    private Instant redeemedAt;

    public boolean isUsableAt(Instant now) {
        return grantStatus == GrantStatus.AVAILABLE && remainingQty > 0
                && (expiresAt == null || expiresAt.isAfter(now));
    }

    public enum GrantStatus {
        AVAILABLE, REDEEMED, EXPIRED, CANCELED
    }
}

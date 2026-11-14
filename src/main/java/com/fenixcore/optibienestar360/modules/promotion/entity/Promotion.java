package com.fenixcore.optibienestar360.modules.promotion.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyProduct;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * A discount offered to members inside a {@link Campaign} (V175, hub ADR
 * 0018): a percentage or, since V255 (hub ADR 0038), a fixed amount in its own
 * currency. Applied to a membership through {@link MembershipPromotion}; the
 * campaign window bounds when it can be applied.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "promotions")
@AttributeOverride(name = "id", column = @Column(name = "promotions_id", nullable = false, updatable = false))
public class Promotion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Column(name = "name", length = 150, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 20, nullable = false)
    private Kind kind = Kind.ACQUISITION;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", length = 20, nullable = false)
    private DiscountType discountType = DiscountType.PERCENTAGE;

    /** Percentage off; only for {@link DiscountType#PERCENTAGE}. */
    @Column(name = "discount_pct", precision = 5, scale = 2)
    private BigDecimal discountPct;

    /** Amount off each discounted charge, in {@link #discountCurrency}; only for {@link DiscountType#FLAT} (V255). */
    @Column(name = "discount_amount", precision = 18, scale = 2)
    private BigDecimal discountAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discount_currency_id")
    private Currency discountCurrency;

    /** Which charges a DISCOUNT covers; {@code null} for an ALLY_BENEFIT (V257). */
    @Enumerated(EnumType.STRING)
    @Column(name = "applies_to", length = 20)
    private AppliesTo appliesTo = AppliesTo.MONTHLY;

    /** Monthly charges discounted; {@code null} = every charge while the campaign runs. */
    @Column(name = "cycles")
    private Integer cycles;

    @Column(name = "covers_extra_beneficiaries", nullable = false)
    private boolean coversExtraBeneficiaries;

    /**
     * The promotion counts its own {@link #maxRedemptions} on top of the
     * campaign quota (V256); without it the promotion has no own limit and
     * only consumes from the campaign.
     */
    @Column(name = "own_quota", nullable = false)
    private boolean ownQuota;

    /** Own cap on how many memberships can take it (only with {@link #ownQuota}); {@code null} = no own cap. */
    @Column(name = "max_redemptions")
    private Integer maxRedemptions;

    /** Occupied slots (pending + confirmed). */
    @Column(name = "redemptions_count", nullable = false)
    private int redemptionsCount;

    @Column(name = "confirmed_count", nullable = false)
    private int confirmedCount;

    @Column(name = "requires_code", nullable = false)
    private boolean requiresCode;

    @Column(name = "accepts_promoter_code", nullable = false)
    private boolean acceptsPromoterCode;

    @Column(name = "accepts_member_code", nullable = false)
    private boolean acceptsMemberCode;

    @Column(name = "accepts_ally_code", nullable = false)
    private boolean acceptsAllyCode;

    /** Optional reward for the referring member who issued the code, granted as a subsidy on their own monthly fee. */
    @Column(name = "referrer_reward_pct", precision = 5, scale = 2)
    private BigDecimal referrerRewardPct;

    @Column(name = "referrer_reward_cycles")
    private Integer referrerRewardCycles;

    // ─── Ally benefit (V257, hub ADR 0038 §7-§8) ─────────────────────────────

    /** DISCOUNT: a discount on the member's fees. ALLY_BENEFIT: a voucher for an ally's offer. */
    @Enumerated(EnumType.STRING)
    @Column(name = "benefit_kind", length = 20, nullable = false)
    private BenefitKind benefitKind = BenefitKind.DISCOUNT;

    /** The ally of the line: required for an ALLY_BENEFIT; forced to the campaign's ally in an exclusive campaign. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ally_id")
    private Ally ally;

    /** The line was proposed by the ally from its portal (campaign open to partnership). */
    @Column(name = "created_by_ally", nullable = false)
    private boolean createdByAlly;

    /** The ally's offer the voucher is for (ALLY_BENEFIT only). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ally_product_id")
    private AllyProduct allyProduct;

    @Enumerated(EnumType.STRING)
    @Column(name = "benefit_type", length = 20)
    private BenefitType benefitType;

    @Column(name = "benefit_pct", precision = 5, scale = 2)
    private BigDecimal benefitPct;

    @Column(name = "benefit_amount", precision = 18, scale = 2)
    private BigDecimal benefitAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "benefit_currency_id")
    private Currency benefitCurrency;

    /** Units of the offer each member gets. */
    @Column(name = "benefit_quantity", nullable = false)
    private int benefitQuantity = 1;

    public boolean isAllyBenefit() {
        return benefitKind == BenefitKind.ALLY_BENEFIT;
    }

    /** Eligible plans; empty = every plan. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "promotion_plans",
            joinColumns = @JoinColumn(name = "promotion_id"),
            inverseJoinColumns = @JoinColumn(name = "plan_id"))
    private Set<Plan> plans = new HashSet<>();

    public boolean discountsInscription() {
        return appliesTo == AppliesTo.INSCRIPTION || appliesTo == AppliesTo.BOTH;
    }

    public boolean discountsMonthly() {
        return appliesTo == AppliesTo.MONTHLY || appliesTo == AppliesTo.BOTH;
    }

    /** Room left under the promotion's own cap (ignores the campaign quota); always true without {@link #ownQuota}. */
    public boolean hasOwnFreeSlot() {
        return !ownQuota || maxRedemptions == null || redemptionsCount < maxRedemptions;
    }

    public boolean isFlat() {
        return discountType == DiscountType.FLAT;
    }

    /** ACQUISITION: new or up-to-date memberships, lost on any delay. RECOVERY: delayed memberships only, survives the delay. */
    public enum Kind {
        ACQUISITION, RECOVERY
    }

    public enum AppliesTo {
        INSCRIPTION, MONTHLY, BOTH
    }

    public enum BenefitKind {
        DISCOUNT, ALLY_BENEFIT
    }

    /** What an ally voucher gives: the offer for free, a percentage off or a fixed amount off. */
    public enum BenefitType {
        FREE, PERCENTAGE, FLAT
    }

    /** PERCENTAGE: {@code discountPct} of each charge. FLAT: {@code discountAmount} (converted) off each charge. */
    public enum DiscountType {
        PERCENTAGE, FLAT
    }
}

package com.fenixcore.optibienestar360.modules.promotion.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
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
 * A % discount offered to members inside a {@link Campaign} (V175, hub ADR
 * 0018). Applied to a membership through {@link MembershipPromotion}; the
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

    @Column(name = "discount_pct", precision = 5, scale = 2, nullable = false)
    private BigDecimal discountPct;

    @Enumerated(EnumType.STRING)
    @Column(name = "applies_to", length = 20, nullable = false)
    private AppliesTo appliesTo = AppliesTo.MONTHLY;

    /** Monthly charges discounted; {@code null} = every charge while the campaign runs. */
    @Column(name = "cycles")
    private Integer cycles;

    @Column(name = "covers_extra_beneficiaries", nullable = false)
    private boolean coversExtraBeneficiaries;

    /** Global cap on how many memberships can take it; {@code null} = no cap. */
    @Column(name = "max_redemptions")
    private Integer maxRedemptions;

    @Column(name = "redemptions_count", nullable = false)
    private int redemptionsCount;

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

    /** ACQUISITION: new or up-to-date memberships, lost on any delay. RECOVERY: delayed memberships only, survives the delay. */
    public enum Kind {
        ACQUISITION, RECOVERY
    }

    public enum AppliesTo {
        INSCRIPTION, MONTHLY, BOTH
    }
}

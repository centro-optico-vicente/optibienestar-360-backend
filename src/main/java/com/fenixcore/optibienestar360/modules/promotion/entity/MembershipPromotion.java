package com.fenixcore.optibienestar360.modules.promotion.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
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

import java.time.Instant;

/**
 * A {@link Promotion} applied to a {@link Membership} (V175). At most one
 * {@code ACTIVE} row per membership. The {@code code_owner_*} columns record
 * who issued the code the member used (at most one).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "membership_promotions")
@AttributeOverride(name = "id", column = @Column(name = "membership_promotions_id", nullable = false, updatable = false))
public class MembershipPromotion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membership_id", nullable = false)
    private Membership membership;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promotion_id", nullable = false)
    private Promotion promotion;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", length = 20, nullable = false)
    private Origin origin;

    @Column(name = "code_used", length = 20)
    private String codeUsed;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "code_owner_promoter_id")
    private Promoter codeOwnerPromoter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "code_owner_member_id")
    private Member codeOwnerMember;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "code_owner_ally_id")
    private Ally codeOwnerAlly;

    /** Monthly charges still to discount; {@code null} = unbounded (the promotion has no cycle limit). */
    @Column(name = "cycles_remaining")
    private Integer cyclesRemaining;

    @Column(name = "inscription_applied", nullable = false)
    private boolean inscriptionApplied;

    @Column(name = "referrer_reward_granted", nullable = false)
    private boolean referrerRewardGranted;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_user_id")
    private User assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "ended_reason", columnDefinition = "text")
    private String endedReason;

    public boolean isOngoing() {
        return Status.ACTIVE.name().equals(getStatus());
    }

    public enum Origin {
        ENROLLMENT, ADMIN
    }

    public enum Status {
        ACTIVE, CONSUMED, CANCELED
    }
}

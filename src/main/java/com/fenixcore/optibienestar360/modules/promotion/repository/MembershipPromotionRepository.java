package com.fenixcore.optibienestar360.modules.promotion.repository;

import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipPromotion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MembershipPromotionRepository extends JpaRepository<MembershipPromotion, Long> {

    /** The membership's single ACTIVE promotion, if any (V175 partial unique index). */
    Optional<MembershipPromotion> findFirstByMembership_IdAndStatusAndActiveTrue(Long membershipId, String status);

    /** The membership's code-from-a-member promotion whose referrer reward is still owed (any status). */
    Optional<MembershipPromotion> findFirstByMembership_IdAndCodeOwnerMemberIsNotNullAndReferrerRewardGrantedFalseAndActiveTrue(
            Long membershipId);

    default Optional<MembershipPromotion> findOngoing(Long membershipId) {
        return findFirstByMembership_IdAndStatusAndActiveTrue(membershipId, MembershipPromotion.Status.ACTIVE.name());
    }
}

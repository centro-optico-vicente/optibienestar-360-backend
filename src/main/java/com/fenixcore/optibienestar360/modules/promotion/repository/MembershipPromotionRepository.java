package com.fenixcore.optibienestar360.modules.promotion.repository;

import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipPromotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MembershipPromotionRepository extends JpaRepository<MembershipPromotion, Long> {

    /** The membership's single ACTIVE promotion, if any (V175 partial unique index). */
    Optional<MembershipPromotion> findFirstByMembership_IdAndStatusAndActiveTrue(Long membershipId, String status);

    /** The membership's code-from-a-member promotion whose referrer reward is still owed (any status). */
    Optional<MembershipPromotion> findFirstByMembership_IdAndCodeOwnerMemberIsNotNullAndReferrerRewardGrantedFalseAndActiveTrue(
            Long membershipId);

    /** Ongoing promotions of ACTIVE memberships whose next payment is due on {@code dueDate}. */
    @Query("""
            select mp from MembershipPromotion mp
              join fetch mp.membership m
              join fetch mp.promotion p
            where mp.status = 'ACTIVE' and mp.active = true
              and m.active = true and m.status = 'ACTIVE' and m.nextDueDate = :dueDate
            """)
    List<MembershipPromotion> findOngoingWithPaymentDueOn(@Param("dueDate") LocalDate dueDate);

    default Optional<MembershipPromotion> findOngoing(Long membershipId) {
        return findFirstByMembership_IdAndStatusAndActiveTrue(membershipId, MembershipPromotion.Status.ACTIVE.name());
    }
}

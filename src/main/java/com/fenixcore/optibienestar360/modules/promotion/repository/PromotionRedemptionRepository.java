package com.fenixcore.optibienestar360.modules.promotion.repository;

import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.promotion.entity.PromotionRedemption;
import com.fenixcore.optibienestar360.modules.promotion.entity.PromotionRedemption.RedemptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PromotionRedemptionRepository extends JpaRepository<PromotionRedemption, Long> {

    Optional<PromotionRedemption> findByMembershipPromotion_Id(Long membershipPromotionId);

    /** Whether the promotion ever took a slot (any status). */
    boolean existsByPromotion_Id(Long promotionId);

    /**
     * The membership's slots in {@code status} (a PENDING one waits for the
     * payment that confirms it). Campaign and promotion are left unloaded on
     * purpose: the caller row-locks them fresh before touching their counters.
     */
    @Query("""
            select r from PromotionRedemption r
              join fetch r.membershipPromotion mp
            where mp.membership.id = :membershipId and r.redemptionStatus = :status
            order by r.reservedAt
            """)
    List<PromotionRedemption> findByMembershipAndStatus(@Param("membershipId") Long membershipId,
                                                        @Param("status") RedemptionStatus status);

    /**
     * PENDING slots that may be past their deadline at {@code now}: their own
     * {@code expires_at} passed, or their campaign is CLOSED with a
     * confirmation grace (the caller applies {@code Campaign#pendingDeadline}).
     */
    @Query("""
            select r from PromotionRedemption r
              join fetch r.membershipPromotion mp
              join r.campaign c
            where r.redemptionStatus = :pending
              and ((r.expiresAt is not null and r.expiresAt <= :now)
                   or (c.lifecycleStatus = :closed
                       and (c.confirmGraceUntil is not null or c.confirmGraceDays is not null)))
            """)
    List<PromotionRedemption> findPendingExpiryCandidates(@Param("now") Instant now,
                                                          @Param("pending") RedemptionStatus pending,
                                                          @Param("closed") Campaign.LifecycleStatus closed);

    default List<PromotionRedemption> findPendingExpiryCandidates(Instant now) {
        return findPendingExpiryCandidates(now, RedemptionStatus.PENDING, Campaign.LifecycleStatus.CLOSED);
    }

    /** CONFIRMED slots whose usage window ended while their membership promotion is still ongoing. */
    @Query("""
            select r from PromotionRedemption r
              join fetch r.membershipPromotion mp
            where r.redemptionStatus = :confirmed
              and r.usageEndsAt is not null and r.usageEndsAt <= :now
              and mp.status = 'ACTIVE' and mp.active = true
            """)
    List<PromotionRedemption> findConfirmedWithUsageEnded(@Param("now") Instant now,
                                                          @Param("confirmed") RedemptionStatus confirmed);

    default List<PromotionRedemption> findConfirmedWithUsageEnded(Instant now) {
        return findConfirmedWithUsageEnded(now, RedemptionStatus.CONFIRMED);
    }

    List<PromotionRedemption> findByCampaignAndRedemptionStatus(Campaign campaign, RedemptionStatus status);

    /** {@code [promotionId, redemptionStatus, count]} rows of a campaign, for the quota report. */
    @Query("""
            select r.promotion.id, r.redemptionStatus, count(r) from PromotionRedemption r
            where r.campaign = :campaign
            group by r.promotion.id, r.redemptionStatus
            """)
    List<Object[]> countByPromotionAndStatus(@Param("campaign") Campaign campaign);

    /** Whether any monthly charge was ever discounted by this membership promotion. */
    @Query("""
            select case when count(c) > 0 then true else false end
            from MembershipCharge c
            where c.membershipPromotion.id = :membershipPromotionId and c.discountAmount > 0
            """)
    boolean anyDiscountedCharge(@Param("membershipPromotionId") Long membershipPromotionId);
}

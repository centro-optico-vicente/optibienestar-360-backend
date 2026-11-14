package com.fenixcore.optibienestar360.modules.promotion.repository;

import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.promotion.entity.PromotionRedemption;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.PromotionRedemption.RedemptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

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
     * confirmation grace or the slot has no deadline of its own (it then ends
     * with the close). The caller applies {@code Campaign#pendingDeadline}.
     */
    @Query("""
            select r from PromotionRedemption r
              join fetch r.membershipPromotion mp
              join r.campaign c
            where r.redemptionStatus = :pending
              and ((r.expiresAt is not null and r.expiresAt <= :now)
                   or (c.lifecycleStatus = :closed
                       and (c.confirmGraceUntil is not null or c.confirmGraceDays is not null
                            or r.expiresAt is null)))
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

    // ─── Reconciliation with the payments (V260, hub ADR 0038 §9) ───────────

    Optional<PromotionRedemption> findByUuid(UUID uuid);

    /** The slots of a campaign, any status (admin slot list). */
    Page<PromotionRedemption> findByCampaign(Campaign campaign, Pageable pageable);

    /** The slots of a campaign in one status (admin slot list filtered by status). */
    Page<PromotionRedemption> findByCampaignAndRedemptionStatus(Campaign campaign, RedemptionStatus status,
                                                               Pageable pageable);

    /** Every slot of a campaign, newest reservation first (payments section, issue report). */
    @Query("""
            select r from PromotionRedemption r
              join fetch r.membershipPromotion mp
            where r.campaign = :campaign
            order by r.reservedAt desc
            """)
    List<PromotionRedemption> findAllOfCampaign(@Param("campaign") Campaign campaign);

    /** The slot a payment confirmed, if any (payment detail). */
    Optional<PromotionRedemption> findFirstByConfirmedByPayment_IdOrderByConfirmedAtDesc(Long paymentId);

    /** Every PENDING slot (hourly reconciliation): the caller locks campaign and slot before touching them. */
    @Query("""
            select r from PromotionRedemption r
              join fetch r.membershipPromotion mp
            where r.redemptionStatus = :pending
            order by r.reservedAt
            """)
    List<PromotionRedemption> findAllInStatus(@Param("pending") RedemptionStatus pending);

    /**
     * PENDING slots not warned yet whose deadline may fall before
     * {@code horizon}: their own {@code expires_at} does, or their campaign
     * is CLOSED (the grace may cut it). The caller applies
     * {@code Campaign#pendingDeadline} and skips the frozen ones.
     */
    @Query("""
            select r from PromotionRedemption r
              join fetch r.membershipPromotion mp
              join r.campaign c
            where r.redemptionStatus = :pending
              and r.expiryNoticeSentAt is null
              and ((r.expiresAt is not null and r.expiresAt <= :horizon) or c.lifecycleStatus = :closed)
            """)
    List<PromotionRedemption> findExpiryNoticeCandidates(@Param("horizon") Instant horizon,
                                                         @Param("pending") RedemptionStatus pending,
                                                         @Param("closed") Campaign.LifecycleStatus closed);

    default List<PromotionRedemption> findExpiryNoticeCandidates(Instant horizon) {
        return findExpiryNoticeCandidates(horizon, RedemptionStatus.PENDING, Campaign.LifecycleStatus.CLOSED);
    }

    /** PENDING ally-benefit slots of a membership with one of {@code allyIds} (validator, info only). */
    @Query("""
            select r from PromotionRedemption r
              join fetch r.membershipPromotion mp
              join fetch r.promotion p
              join fetch r.campaign c
            where mp.membership.id = :membershipId
              and r.redemptionStatus = :pending
              and p.benefitKind = :allyBenefit
              and p.ally.id in :allyIds
            order by r.reservedAt
            """)
    List<PromotionRedemption> findPendingAllyBenefits(@Param("membershipId") Long membershipId,
                                                      @Param("allyIds") Collection<Long> allyIds,
                                                      @Param("pending") RedemptionStatus pending,
                                                      @Param("allyBenefit") Promotion.BenefitKind allyBenefit);

    default List<PromotionRedemption> findPendingAllyBenefits(Long membershipId, Collection<Long> allyIds) {
        return findPendingAllyBenefits(membershipId, allyIds, RedemptionStatus.PENDING, Promotion.BenefitKind.ALLY_BENEFIT);
    }

    /** PENDING ally-benefit slots of a membership, any ally (member portal, {@code includePending}). */
    @Query("""
            select r from PromotionRedemption r
              join fetch r.membershipPromotion mp
              join fetch r.promotion p
              join fetch r.campaign c
            where mp.membership.id = :membershipId
              and r.redemptionStatus = :pending
              and p.benefitKind = :allyBenefit
            order by r.reservedAt desc
            """)
    List<PromotionRedemption> findPendingAllyBenefitsOfMembership(@Param("membershipId") Long membershipId,
                                                                  @Param("pending") RedemptionStatus pending,
                                                                  @Param("allyBenefit") Promotion.BenefitKind allyBenefit);

    default List<PromotionRedemption> findPendingAllyBenefitsOfMembership(Long membershipId) {
        return findPendingAllyBenefitsOfMembership(membershipId, RedemptionStatus.PENDING, Promotion.BenefitKind.ALLY_BENEFIT);
    }
}

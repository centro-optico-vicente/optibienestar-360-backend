package com.fenixcore.optibienestar360.modules.promotion.repository;

import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipBenefitGrant;
import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipBenefitGrant.GrantStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipBenefitGrantRepository extends JpaRepository<MembershipBenefitGrant, Long> {

    Optional<MembershipBenefitGrant> findByMembershipPromotion_Id(Long membershipPromotionId);

    /** Row lock taken before redeeming a unit, so two counters can't redeem the same last unit. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from MembershipBenefitGrant g where g.uuid = :uuid")
    Optional<MembershipBenefitGrant> findForUpdateByUuid(@Param("uuid") UUID uuid);

    /** The membership's grants with one of the caller's allies, in {@code status} (validator). */
    @Query("""
            select g from MembershipBenefitGrant g
            where g.membership.id = :membershipId and g.grantStatus = :status and g.ally.id in :allyIds
            order by g.grantedAt
            """)
    List<MembershipBenefitGrant> findForMembershipAndAllies(@Param("membershipId") Long membershipId,
                                                            @Param("status") GrantStatus status,
                                                            @Param("allyIds") Collection<Long> allyIds);

    List<MembershipBenefitGrant> findByMembership_IdOrderByGrantedAtDesc(Long membershipId);

    /** AVAILABLE grants already past their expiry (job). */
    @Query("""
            select g from MembershipBenefitGrant g
            where g.grantStatus = :status and g.expiresAt is not null and g.expiresAt <= :now
            """)
    List<MembershipBenefitGrant> findExpiring(@Param("status") GrantStatus status, @Param("now") Instant now);

    /** {@code [promotionId, grantStatus, grants, redeemedUnits]} rows of a campaign, for the reconciliation report. */
    @Query("""
            select g.promotion.id, g.grantStatus, count(g), sum(g.quantity - g.remainingQty)
            from MembershipBenefitGrant g
            where g.promotion.campaign = :campaign
            group by g.promotion.id, g.grantStatus
            """)
    List<Object[]> countByPromotionAndStatus(@Param("campaign") Campaign campaign);
}

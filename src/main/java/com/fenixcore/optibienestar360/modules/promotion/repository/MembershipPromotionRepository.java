package com.fenixcore.optibienestar360.modules.promotion.repository;

import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipPromotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.BenefitKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MembershipPromotionRepository extends JpaRepository<MembershipPromotion, Long> {

    /** The membership's single ACTIVE promotion of {@code benefitKind}, if any (V257 partial unique index for DISCOUNT). */
    Optional<MembershipPromotion> findFirstByMembership_IdAndStatusAndBenefitKindAndActiveTrue(
            Long membershipId, String status, BenefitKind benefitKind);

    /** The membership's ACTIVE promotions of {@code benefitKind} (several ALLY_BENEFIT ones may coexist). */
    List<MembershipPromotion> findByMembership_IdAndStatusAndBenefitKindAndActiveTrue(
            Long membershipId, String status, BenefitKind benefitKind);

    boolean existsByMembership_IdAndPromotion_IdAndStatusAndActiveTrue(Long membershipId, Long promotionId, String status);

    /** The membership's code-from-a-member promotion whose referrer reward is still owed (any status). */
    Optional<MembershipPromotion> findFirstByMembership_IdAndCodeOwnerMemberIsNotNullAndReferrerRewardGrantedFalseAndActiveTrue(
            Long membershipId);

    /** Ongoing promotions of {@code benefitKind} of ACTIVE memberships whose next payment is due on {@code dueDate}. */
    @Query("""
            select mp from MembershipPromotion mp
              join fetch mp.membership m
              join fetch mp.promotion p
            where mp.status = 'ACTIVE' and mp.active = true and mp.benefitKind = :benefitKind
              and m.active = true and m.status = 'ACTIVE' and m.nextDueDate = :dueDate
            """)
    List<MembershipPromotion> findOngoingWithPaymentDueOn(@Param("dueDate") LocalDate dueDate,
                                                          @Param("benefitKind") BenefitKind benefitKind);

    /** Payment reminders only concern the fee discount, never an ally voucher. */
    default List<MembershipPromotion> findOngoingWithPaymentDueOn(LocalDate dueDate) {
        return findOngoingWithPaymentDueOn(dueDate, BenefitKind.DISCOUNT);
    }

    /** The membership's ongoing fee DISCOUNT promotion (at most one). */
    default Optional<MembershipPromotion> findOngoing(Long membershipId) {
        return findFirstByMembership_IdAndStatusAndBenefitKindAndActiveTrue(
                membershipId, MembershipPromotion.Status.ACTIVE.name(), BenefitKind.DISCOUNT);
    }

    /** The membership's ongoing ALLY_BENEFIT promotions. */
    default List<MembershipPromotion> findOngoingAllyBenefits(Long membershipId) {
        return findByMembership_IdAndStatusAndBenefitKindAndActiveTrue(
                membershipId, MembershipPromotion.Status.ACTIVE.name(), BenefitKind.ALLY_BENEFIT);
    }
}

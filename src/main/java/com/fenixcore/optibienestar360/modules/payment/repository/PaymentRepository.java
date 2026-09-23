package com.fenixcore.optibienestar360.modules.payment.repository;

import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface PaymentRepository extends JpaRepository<Payment, Long>,
        JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByUuid(UUID uuid);

    /**
     * Powers {@code GET /v1/me/payments} — walks
     * {@code Payment → Membership → Member → Person} back to the User's
     * person, matching by {@code user.uuid}. Same single-JPQL shape as
     * {@code MemberRepository.findByUserUuid} so the path is index-friendly
     * (no fetch joins, the controller maps to a DTO that doesn't need the
     * nested associations).
     */
    @Query("SELECT p FROM Payment p " +
           "WHERE p.active = true " +
           "  AND p.membership.member.person.id = " +
           "      (SELECT u.person.id FROM User u WHERE u.uuid = :userUuid)")
    Page<Payment> findOwnByUserUuid(@Param("userUuid") UUID userUuid, Pageable pageable);

    /**
     * Number of APPROVED payments across all of a member's memberships.
     * Used by {@code PaymentsService.approve} to detect the member's
     * <i>first</i> approved payment (stamps {@code Member.confirmedAt}).
     */
    @Query("SELECT COUNT(p) FROM Payment p " +
           "WHERE p.status = 'APPROVED' " +
           "  AND p.membership.member.id = :memberId")
    long countApprovedByMemberId(@Param("memberId") Long memberId);

    /**
     * APPROVED collections (direction=IN) directly attributed to a promoter
     * within a window — feeds the {@code AMOUNT_COLLECTED} bonus metric
     * (I-BE, hub plan Part I). Uses the denormalized {@code Payment.promoter}
     * FK (same one {@code CommissionPayoutService} and "cobros de mi red"
     * filtering already rely on), not a walk through the member's referrer.
     */
    @Query("SELECT p FROM Payment p " +
           "WHERE p.status = 'APPROVED' " +
           "  AND p.direction = 'IN' " +
           "  AND p.promoter.id = :promoterId " +
           "  AND p.paymentDate >= :from AND p.paymentDate < :to")
    java.util.List<Payment> findApprovedInForPromoterInWindow(
            @Param("promoterId") Long promoterId,
            @Param("from") java.time.Instant from,
            @Param("to") java.time.Instant to);

    /**
     * Same as {@link #findApprovedInForPromoterInWindow}, but scoped to a
     * whole set of promoters — powers the {@code basis=AMOUNT} team-volume
     * check on {@code HierarchyOverrideTier} (Fase A, phase 2), the
     * team-subtree analogue of the single-promoter AMOUNT_COLLECTED bonus
     * check.
     */
    @Query("SELECT p FROM Payment p " +
           "WHERE p.status = 'APPROVED' " +
           "  AND p.direction = 'IN' " +
           "  AND p.promoter.id IN :promoterIds " +
           "  AND p.paymentDate >= :from AND p.paymentDate < :to")
    java.util.List<Payment> findApprovedInForPromotersInWindow(
            @Param("promoterIds") java.util.Collection<Long> promoterIds,
            @Param("from") java.time.Instant from,
            @Param("to") java.time.Instant to);
}

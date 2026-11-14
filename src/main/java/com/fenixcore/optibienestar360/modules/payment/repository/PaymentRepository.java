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
     * Powers {@code GET /v1/me/payments} — the collections the user paid (payer person), the ones credited to them
     * (beneficiary person, V203) and the ones settling their own membership, matched by {@code user.uuid}. Membership
     * is optional (V201), so it is LEFT-joined: a payment with no membership still shows to its payer. Same single-JPQL shape as
     * {@code MemberRepository.findByUserUuid} so the path is index-friendly
     * (no fetch joins, the controller maps to a DTO that doesn't need the
     * nested associations).
     */
    @Query("SELECT p FROM Payment p " +
           "LEFT JOIN p.membership m LEFT JOIN m.member mm " +
           "WHERE p.active = true AND p.direction = 'IN' " +
           "  AND (p.person.id = (SELECT u.person.id FROM User u WHERE u.uuid = :userUuid) " +
           "    OR p.beneficiaryPerson.id = (SELECT u.person.id FROM User u WHERE u.uuid = :userUuid) " +
           "    OR mm.person.id = (SELECT u.person.id FROM User u WHERE u.uuid = :userUuid)) " +
           "  AND (:dateFrom IS NULL OR p.paymentDate >= :dateFrom) " +
           "  AND (:dateTo IS NULL OR p.paymentDate < :dateTo)")
    Page<Payment> findOwnByUserUuid(@Param("userUuid") UUID userUuid,
                                    @Param("dateFrom") java.time.Instant dateFrom,
                                    @Param("dateTo") java.time.Instant dateTo, Pageable pageable);

    /** Unbounded variant. */
    default Page<Payment> findOwnByUserUuid(UUID userUuid, Pageable pageable) {
        return findOwnByUserUuid(userUuid, null, null, pageable);
    }

    /**
     * Same as {@link #findOwnByUserUuid} plus a free-text {@code :q} (accent/case-insensitive, via the
     * {@code immutable_unaccent} wrapper) over the payment's notes, file name, beneficiary snapshots, the
     * payer/beneficiary names and any line's reference/identification.
     */
    @Query("SELECT p FROM Payment p " +
           "LEFT JOIN p.membership m LEFT JOIN m.member mm " +
           "LEFT JOIN p.beneficiaryPerson bp " +
           "WHERE p.active = true AND p.direction = 'IN' " +
           "  AND (p.person.id = (SELECT u.person.id FROM User u WHERE u.uuid = :userUuid) " +
           "    OR p.beneficiaryPerson.id = (SELECT u.person.id FROM User u WHERE u.uuid = :userUuid) " +
           "    OR mm.person.id = (SELECT u.person.id FROM User u WHERE u.uuid = :userUuid)) " +
           "  AND (cast(function('immutable_unaccent', lower(coalesce(p.adminNotes, ''))) as string) LIKE cast(function('immutable_unaccent', lower(concat('%', :q, '%'))) as string) " +
           "    OR cast(function('immutable_unaccent', lower(coalesce(p.supportFileName, ''))) as string) LIKE cast(function('immutable_unaccent', lower(concat('%', :q, '%'))) as string) " +
           "    OR cast(function('immutable_unaccent', lower(coalesce(p.beneficiaryDocument, ''))) as string) LIKE cast(function('immutable_unaccent', lower(concat('%', :q, '%'))) as string) " +
           "    OR cast(function('immutable_unaccent', lower(coalesce(p.beneficiaryContractNumber, ''))) as string) LIKE cast(function('immutable_unaccent', lower(concat('%', :q, '%'))) as string) " +
           "    OR cast(function('immutable_unaccent', lower(p.person.fullName)) as string) LIKE cast(function('immutable_unaccent', lower(concat('%', :q, '%'))) as string) " +
           "    OR cast(function('immutable_unaccent', lower(coalesce(bp.fullName, ''))) as string) LIKE cast(function('immutable_unaccent', lower(concat('%', :q, '%'))) as string) " +
           "    OR EXISTS (SELECT 1 FROM PaymentLine l WHERE l.payment = p AND l.active = true " +
           "         AND (cast(function('immutable_unaccent', lower(coalesce(l.referenceNumber, ''))) as string) LIKE cast(function('immutable_unaccent', lower(concat('%', :q, '%'))) as string) " +
           "           OR cast(function('immutable_unaccent', lower(coalesce(l.identification, ''))) as string) LIKE cast(function('immutable_unaccent', lower(concat('%', :q, '%'))) as string)))) " +
           "  AND (:dateFrom IS NULL OR p.paymentDate >= :dateFrom) " +
           "  AND (:dateTo IS NULL OR p.paymentDate < :dateTo)")
    Page<Payment> findOwnByUserUuidAndSearch(@Param("userUuid") UUID userUuid, @Param("q") String q,
                                             @Param("dateFrom") java.time.Instant dateFrom,
                                             @Param("dateTo") java.time.Instant dateTo, Pageable pageable);

    /** Unbounded variant. */
    default Page<Payment> findOwnByUserUuidAndSearch(UUID userUuid, String q, Pageable pageable) {
        return findOwnByUserUuidAndSearch(userUuid, q, null, null, pageable);
    }

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

    /**
     * Competitive commission rules (Fase 2a) — {@code SALES_*} candidates: approved inscription
     * (one-time fee) payments. Widens the window to a match on ANY of the 3 achievement-date-basis
     * columns ({@code paymentDate}/{@code receivedAt}/{@code reviewedAt}) so no candidate is
     * missed at the SQL layer; the provider re-filters precisely on the rule's actual basis column
     * in Java (JPQL can't select a dynamic column).
     */
    @Query("""
            SELECT p FROM Payment p
            WHERE p.status = 'APPROVED' AND p.direction = 'IN' AND p.inscription = true
              AND p.promoter.id IS NOT NULL
              AND (:includeSystem = true OR p.promoter.system = false)
              AND (:typeIds IS NULL OR p.promoter.promoterType.id IN :typeIds)
              AND (:rankIds IS NULL OR p.promoter.rank.id IN :rankIds)
              AND (p.paymentDate BETWEEN :from AND :to
                   OR p.receivedAt BETWEEN :from AND :to
                   OR p.reviewedAt BETWEEN :from AND :to)
            """)
    java.util.List<Payment> findSalesCandidatesInWindow(@Param("from") java.time.Instant from,
                                                        @Param("to") java.time.Instant to,
                                                        @Param("includeSystem") boolean includeSystem,
                                                        @Param("typeIds") java.util.Collection<Long> typeIds,
                                                        @Param("rankIds") java.util.Collection<Long> rankIds);

    /** Same as {@link #findSalesCandidatesInWindow}, but {@code COLLECTION_*}: recurring (non-inscription) payments. */
    @Query("""
            SELECT p FROM Payment p
            WHERE p.status = 'APPROVED' AND p.direction = 'IN' AND p.inscription = false
              AND p.promoter.id IS NOT NULL
              AND (:includeSystem = true OR p.promoter.system = false)
              AND (:typeIds IS NULL OR p.promoter.promoterType.id IN :typeIds)
              AND (:rankIds IS NULL OR p.promoter.rank.id IN :rankIds)
              AND (p.paymentDate BETWEEN :from AND :to
                   OR p.receivedAt BETWEEN :from AND :to
                   OR p.reviewedAt BETWEEN :from AND :to)
            """)
    java.util.List<Payment> findCollectionCandidatesInWindow(@Param("from") java.time.Instant from,
                                                             @Param("to") java.time.Instant to,
                                                             @Param("includeSystem") boolean includeSystem,
                                                             @Param("typeIds") java.util.Collection<Long> typeIds,
                                                             @Param("rankIds") java.util.Collection<Long> rankIds);

    /**
     * Same as {@link #findSalesCandidatesInWindow}, but {@code ADVANCE_*}: payments that cover
     * more than one period ahead ({@code coverageThroughPeriod > appliedPeriod}, unblocked by #289).
     */
    @Query("""
            SELECT p FROM Payment p
            WHERE p.status = 'APPROVED' AND p.direction = 'IN'
              AND p.coverageThroughPeriod IS NOT NULL AND p.coverageThroughPeriod > p.appliedPeriod
              AND p.promoter.id IS NOT NULL
              AND (:includeSystem = true OR p.promoter.system = false)
              AND (:typeIds IS NULL OR p.promoter.promoterType.id IN :typeIds)
              AND (:rankIds IS NULL OR p.promoter.rank.id IN :rankIds)
              AND (p.paymentDate BETWEEN :from AND :to
                   OR p.receivedAt BETWEEN :from AND :to
                   OR p.reviewedAt BETWEEN :from AND :to)
            """)
    java.util.List<Payment> findAdvanceCandidatesInWindow(@Param("from") java.time.Instant from,
                                                          @Param("to") java.time.Instant to,
                                                          @Param("includeSystem") boolean includeSystem,
                                                          @Param("typeIds") java.util.Collection<Long> typeIds,
                                                          @Param("rankIds") java.util.Collection<Long> rankIds);

    /**
     * The membership's collections (IN) of one kind — inscription or monthly
     * fee — in one of {@code statuses}, newest registration first. A campaign
     * slot reserved after its payment already exists confirms at once with it
     * (hub ADR 0038 §5).
     */
    @Query("""
            SELECT p FROM Payment p
            WHERE p.membership.id = :membershipId
              AND p.direction = 'IN'
              AND p.inscription = :inscription
              AND p.active = true
              AND p.status IN :statuses
            ORDER BY p.createdAt DESC
            """)
    java.util.List<Payment> findCollectionsOfMembership(@Param("membershipId") Long membershipId,
                                                        @Param("inscription") boolean inscription,
                                                        @Param("statuses") java.util.Collection<String> statuses);
}

package com.fenixcore.optibienestar360.modules.member.repository;

import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterEnrollmentEventRow;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterMemberRow;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterMetricCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface MemberRepository extends JpaRepository<Member, Long>, JpaSpecificationExecutor<Member> {

    Optional<Member> findByUuid(UUID uuid);

    /** A promoter's whole portfolio — every member currently attributed to them. */
    List<Member> findByPromoter_Id(Long promoterId);

    boolean existsByUuid(UUID uuid);

    /** Usage check for {@code PromotersService.countUsages} — ALL rows (active + inactive). */
    long countByPromoterId(Long promoterId);

    /** "Is this person already enrolled as a Member?" — backs the dedup guard at affiliation. */
    boolean existsByPersonId(Long personId);

    /** Usage count for {@code Occupation} delete/reactivation checks — see {@code OccupationService}. */
    long countByOccupation_Uuid(UUID uuid);

    /** Used by the validator + the digital card flow to resolve a member from their cédula. */
    Optional<Member> findByPersonId(Long personId);

    /** A corporate contract's active member portfolio (V38) — backs the list endpoint. */
    Page<Member> findByCorporateContractIdAndActiveTrue(Long corporateContractId, Pageable pageable);

    /** Live enrolled headcount for a contract — the service syncs {@code actual_member_count} to this. */
    long countByCorporateContractIdAndActiveTrue(Long corporateContractId);

    /**
     * Affiliate-side referral code lookup (V27 {@code members.referral_code}).
     * Used by {@code ReferralService} to resolve a code captured at
     * enrollment to the referrer Member. The unique partial index in V27
     * keeps this O(log n) without conflict against null / back-fill rows.
     */
    Optional<Member> findByReferralCode(String referralCode);

    /**
     * Resolve the member of the user identified by JWT subject. Backs
     * {@code GET /v1/me/member} — walks the {@code user.person} link to find
     * the matching {@link Member} in a single query without loading the User.
     */
    @Query("SELECT m FROM Member m WHERE m.person.id = " +
           "(SELECT u.person.id FROM User u WHERE u.uuid = :userUuid)")
    Optional<Member> findByUserUuid(@Param("userUuid") UUID userUuid);

    /**
     * A promoter's portfolio for the self-service dashboard
     * ({@code GET /v1/me/advisor}): every active member attributed to the
     * promoter, each with the status of its currently-active membership (via a
     * LEFT JOIN so members without one still appear, with null membership
     * fields). Returns the flat {@link PromoterMemberRow} projection so the
     * status join costs no N+1. The V21 partial UNIQUE
     * {@code (member_id) WHERE is_active=TRUE} guarantees at most one active
     * membership per member, so no row duplication.
     */
    @Query("""
            SELECT new com.fenixcore.optibienestar360.modules.promoter.dto.PromoterMemberRow(
                m.uuid, m.person.fullName, m.person.documentNumber, m.person.phone, pl.name, m.enrolledAt,
                ms.status, ms.nextDueDate, ms.lastPaidThrough, ms.monthlyFee)
            FROM Member m
            LEFT JOIN Membership ms ON ms.member = m AND ms.active = true
            LEFT JOIN ms.plan pl
            WHERE m.active = true AND m.promoter.id = :promoterId
            ORDER BY m.person.fullName
            """)
    List<PromoterMemberRow> findPromoterPortfolio(@Param("promoterId") Long promoterId);

    /**
     * Bonus-engine metric: new subscribers per promoter enrolled within a window
     * (by {@code enrolled_at}). One grouped row per promoter with ≥1 new member.
     * {@code includeSystem=false} excludes the INSTITUCION system row so lifetime
     * / campaign milestones only reward humans.
     */
    @Query("""
            SELECT new com.fenixcore.optibienestar360.modules.promoter.dto.PromoterMetricCount(
                m.promoter.id, COUNT(m.id))
            FROM Member m
            WHERE m.active = true
              AND m.promoter.id IS NOT NULL
              AND m.enrolledAt BETWEEN :start AND :end
              AND (:includeSystem = true OR m.promoter.system = false)
            GROUP BY m.promoter.id
            """)
    List<PromoterMetricCount> countNewSubscribersByPromoter(@Param("start") LocalDate start,
                                                            @Param("end") LocalDate end,
                                                            @Param("includeSystem") boolean includeSystem);

    /**
     * The single definition of a "confirmed enrollment" for the commission-tier count: an
     * active member whose {@code confirmed_at} is stamped (V45) — automatically on the first
     * APPROVED payment ({@code PaymentsService.confirmMemberOnFirstApprovedPayment}) or manually
     * by an admin ({@code MEMBER_CONFIRM}, subsidized members that never pay). PENDING enrollments
     * (no approved payment yet), rejected ones (never stamped) and cancelled/deactivated members
     * ({@code active = false}) therefore do not count. Change the confirmation rule HERE only
     * (hub ADR 0013 "Confirmados"; see {@code .ai/notes/2026-10-25-confirmed-enrollment-count.md}).
     */
    String CONFIRMED_ENROLLMENT_PREDICATE = "m.active = true AND m.confirmedAt IS NOT NULL";

    /**
     * Commission-tier engine: how many <b>confirmed</b> new subscribers a single promoter
     * obtained within a period — {@code confirmed_at} falling inside {@code [start, end]}
     * (inclusive local dates in the app timezone), NOT {@code enrolled_at}. Drives tier
     * qualification (threshold_count). {@code confirmed_at} is write-once, so a closed window's
     * count is stable (hub ADR 0017: already-generated commissions are never recalculated).
     * Delegates to {@link #countConfirmedSubscribersForPromoterBetween}.
     */
    default long countNewSubscribersForPromoter(Long promoterId, LocalDate start, LocalDate end) {
        return countConfirmedSubscribersForPromoterBetween(promoterId,
                start.atStartOfDay(AppTimeZone.ZONE).toInstant(),
                end.plusDays(1).atStartOfDay(AppTimeZone.ZONE).toInstant());
    }

    /** Instant-based half-open {@code [from, to)} form of {@link #countNewSubscribersForPromoter}; see {@link #CONFIRMED_ENROLLMENT_PREDICATE}. */
    @Query("SELECT COUNT(m) FROM Member m WHERE m.promoter.id = :promoterId AND "
            + CONFIRMED_ENROLLMENT_PREDICATE
            + " AND m.confirmedAt >= :from AND m.confirmedAt < :to")
    long countConfirmedSubscribersForPromoterBetween(@Param("promoterId") Long promoterId,
                                                     @Param("from") Instant from,
                                                     @Param("to") Instant to);

    /**
     * Hierarchy-override engine (V102): same as {@link
     * #countNewSubscribersForPromoter} but summed across an entire team
     * subtree ({@code PromoterHierarchyService.resolveTeamMemberIds}) —
     * drives INSCRIPTION {@code hierarchy_override_tiers} qualification.
     */
    @Query("""
            SELECT COUNT(m) FROM Member m
            WHERE m.active = true
              AND m.promoter.id IN :promoterIds
              AND m.enrolledAt BETWEEN :start AND :end
            """)
    long countNewSubscribersForPromoters(@Param("promoterIds") Collection<Long> promoterIds,
                                         @Param("start") LocalDate start,
                                         @Param("end") LocalDate end);

    /**
     * Bonus-engine metric: active subscribers per promoter — members whose
     * currently-active membership is in status ACTIVE. The V21 partial UNIQUE
     * (one active membership per member) keeps the DISTINCT count exact.
     */
    @Query("""
            SELECT new com.fenixcore.optibienestar360.modules.promoter.dto.PromoterMetricCount(
                m.promoter.id, COUNT(DISTINCT m.id))
            FROM Membership ms
            JOIN ms.member m
            WHERE ms.active = true
              AND ms.status = 'ACTIVE'
              AND m.active = true
              AND m.promoter.id IS NOT NULL
              AND (:includeSystem = true OR m.promoter.system = false)
            GROUP BY m.promoter.id
            """)
    List<PromoterMetricCount> countActiveSubscribersByPromoter(@Param("includeSystem") boolean includeSystem);

    /**
     * Competitive commission rules (hub plan competitive-commission-rules, Fase 2a):
     * {@code NEW_SUBSCRIBERS} raw enrollment events within a window, scoped to the rule's
     * promoter types / ranks (either filter {@code null} = "all"). {@code
     * CompetitiveRankingEngine.firstToReach} folds these per promoter for FIRST_TO_REACH; the
     * provider groups them itself for a RANKING snapshot — one query serves both.
     */
    @Query("""
            SELECT new com.fenixcore.optibienestar360.modules.promoter.dto.PromoterEnrollmentEventRow(
                m.promoter.id, m.enrolledAt)
            FROM Member m
            WHERE m.active = true
              AND m.promoter.id IS NOT NULL
              AND m.enrolledAt BETWEEN :start AND :end
              AND (:includeSystem = true OR m.promoter.system = false)
              AND (:typeIds IS NULL OR m.promoter.promoterType.id IN :typeIds)
              AND (:rankIds IS NULL OR m.promoter.rank.id IN :rankIds)
            """)
    List<PromoterEnrollmentEventRow> findNewSubscriberEvents(@Param("start") LocalDate start,
                                                             @Param("end") LocalDate end,
                                                             @Param("includeSystem") boolean includeSystem,
                                                             @Param("typeIds") Collection<Long> typeIds,
                                                             @Param("rankIds") Collection<Long> rankIds);

    /**
     * Competitive commission rules: {@code ACTIVE_SUBSCRIBERS} snapshot (RANKING only — this
     * metric has no "moment it happened", see {@code ActiveSubscribersMetricProvider}), scoped
     * the same way as {@link #findNewSubscriberEvents}.
     */
    @Query("""
            SELECT new com.fenixcore.optibienestar360.modules.promoter.dto.PromoterMetricCount(
                m.promoter.id, COUNT(DISTINCT m.id))
            FROM Membership ms
            JOIN ms.member m
            WHERE ms.active = true
              AND ms.status = 'ACTIVE'
              AND m.active = true
              AND m.promoter.id IS NOT NULL
              AND (:includeSystem = true OR m.promoter.system = false)
              AND (:typeIds IS NULL OR m.promoter.promoterType.id IN :typeIds)
              AND (:rankIds IS NULL OR m.promoter.rank.id IN :rankIds)
            GROUP BY m.promoter.id
            """)
    List<PromoterMetricCount> countActiveSubscribersByPromoterScoped(@Param("includeSystem") boolean includeSystem,
                                                                     @Param("typeIds") Collection<Long> typeIds,
                                                                     @Param("rankIds") Collection<Long> rankIds);
}

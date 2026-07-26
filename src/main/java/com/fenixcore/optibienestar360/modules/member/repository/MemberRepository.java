package com.fenixcore.optibienestar360.modules.member.repository;

import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterMemberRow;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterMetricCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface MemberRepository extends JpaRepository<Member, Long>, JpaSpecificationExecutor<Member> {

    Optional<Member> findByUuid(UUID uuid);

    /** "Is this person already enrolled as a Member?" — backs the dedup guard at affiliation. */
    boolean existsByPersonId(Long personId);

    /** Used by the validator + the digital card flow to resolve a member from their cédula. */
    Optional<Member> findByPersonId(Long personId);

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
     * ({@code GET /v1/promoter/me}): every active member attributed to the
     * promoter, each with the status of its currently-active membership (via a
     * LEFT JOIN so members without one still appear, with null membership
     * fields). Returns the flat {@link PromoterMemberRow} projection so the
     * status join costs no N+1. The V21 partial UNIQUE
     * {@code (member_id) WHERE is_active=TRUE} guarantees at most one active
     * membership per member, so no row duplication.
     */
    @Query("""
            SELECT new com.fenixcore.optibienestar360.modules.promoter.dto.PromoterMemberRow(
                m.uuid, m.person.fullName, ms.status, ms.nextDueDate, ms.monthlyFee)
            FROM Member m
            LEFT JOIN Membership ms ON ms.member = m AND ms.active = true
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
}

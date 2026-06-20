package com.fenixcore.optisaludplus.modules.promoter.repository;

import com.fenixcore.optisaludplus.modules.promoter.entity.Referral;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface ReferralRepository extends JpaRepository<Referral, Long>,
        JpaSpecificationExecutor<Referral> {

    Optional<Referral> findByUuid(UUID uuid);

    /**
     * "Was this person referred by someone?" — backs the resolution check
     * before creating a new referral row, and the reverse-lookup on the
     * affiliate detail page.
     */
    @Query("""
            SELECT r FROM Referral r
            WHERE r.referred.id = :referredMemberId
              AND r.active = true
              AND r.status NOT IN ('EXPIRED', 'VOIDED')
            """)
    Optional<Referral> findActiveByReferredMember(@Param("referredMemberId") Long referredMemberId);

    /**
     * Unclaimed referrals for the given referrer — REGISTERED rows where
     * the reward hasn't been granted yet. The reward-application step
     * picks these FIFO (oldest enrolled first) and marks them
     * REWARD_GRANTED.
     */
    @Query("""
            SELECT r FROM Referral r
            WHERE r.referrer.id = :referrerMemberId
              AND r.active = true
              AND r.status = 'REGISTERED'
              AND r.rewardGrantedAt IS NULL
            ORDER BY r.enrolledAt ASC
            """)
    List<Referral> findUnclaimedByReferrer(@Param("referrerMemberId") Long referrerMemberId);

    /** Anti-dup guard before creating a referral row. */
    @Query("""
            SELECT (COUNT(r) > 0) FROM Referral r
            WHERE r.referrer.id = :referrerMemberId
              AND r.referred.id = :referredMemberId
              AND r.status NOT IN ('EXPIRED', 'VOIDED')
            """)
    boolean existsActiveByReferrerAndReferred(
            @Param("referrerMemberId") Long referrerMemberId,
            @Param("referredMemberId") Long referredMemberId);

    /**
     * Powers {@code GET /v1/me/referrals} — paginated history of referrals
     * where the logged-in user is the REFERRER. Walks
     * {@code user → person → member.id} in a single subquery, same shape
     * {@code PaymentRepository.findOwnByUserUuid} uses.
     *
     * <p>Default ordering is left to the caller's {@link Pageable} —
     * {@code createdAt DESC} covers all statuses (PENDING rows that
     * never enrolled don't have {@code enrolledAt}).</p>
     */
    @Query("""
            SELECT r FROM Referral r
            WHERE r.active = true
              AND r.referrer.id = (
                  SELECT m.id FROM Member m
                  WHERE m.person.id = (
                      SELECT u.person.id FROM User u WHERE u.uuid = :userUuid
                  )
              )
            """)
    Page<Referral> findOwnByUserUuid(@Param("userUuid") UUID userUuid, Pageable pageable);
}

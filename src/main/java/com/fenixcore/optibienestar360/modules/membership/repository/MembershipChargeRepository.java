package com.fenixcore.optibienestar360.modules.membership.repository;

import com.fenixcore.optibienestar360.modules.membership.entity.MembershipCharge;
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

@Transactional(readOnly = true)
public interface MembershipChargeRepository extends JpaRepository<MembershipCharge, Long>,
        JpaSpecificationExecutor<MembershipCharge> {

    /** Idempotency check behind {@code MembershipChargeService.ensureChargeForPeriod} (V153 unique constraint). */
    Optional<MembershipCharge> findByMembership_IdAndPeriodStart(Long membershipId, LocalDate periodStart);

    boolean existsByMembership_IdAndPeriodStart(Long membershipId, LocalDate periodStart);

    /**
     * Wide net for {@code OVERDUE_SETTLED_COUNT/AMOUNT} (hub plan competitive-commission-rules,
     * Fase 5, S3 — "vencida saldada"): every {@code COVERED} charge whose covering payment landed
     * on any of the 3 achievement-date-basis columns inside {@code [from, to)} — same
     * wide-then-refine shape {@code PaymentRepository}'s sales/collection/advance candidate
     * queries use. The precise basis pick, the window re-check and the "paid after due_date"
     * condition itself (what actually makes it "vencida saldada", not just any covered charge)
     * all happen in Java — see {@code OverdueSettledMetricProviders}.
     */
    @Query("""
            SELECT mc FROM MembershipCharge mc
            JOIN mc.coveredByPayment p
            WHERE mc.active = true
              AND mc.status = 'COVERED'
              AND p.status = 'APPROVED' AND p.direction = 'IN'
              AND p.promoter.id IS NOT NULL
              AND (:includeSystem = true OR p.promoter.system = false)
              AND (:typeIds IS NULL OR p.promoter.promoterType.id IN :typeIds)
              AND (:rankIds IS NULL OR p.promoter.rank.id IN :rankIds)
              AND (p.paymentDate BETWEEN :from AND :to
                   OR p.receivedAt BETWEEN :from AND :to
                   OR p.reviewedAt BETWEEN :from AND :to)
            """)
    List<MembershipCharge> findOverdueSettledCandidatesInWindow(@Param("from") Instant from,
                                                                 @Param("to") Instant to,
                                                                 @Param("includeSystem") boolean includeSystem,
                                                                 @Param("typeIds") Collection<Long> typeIds,
                                                                 @Param("rankIds") Collection<Long> rankIds);
}

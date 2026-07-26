package com.fenixcore.optibienestar360.modules.subsidy.repository;

import com.fenixcore.optibienestar360.modules.subsidy.entity.Subsidy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Transactional(readOnly = true)
public interface SubsidyRepository extends JpaRepository<Subsidy, Long>,
        JpaSpecificationExecutor<Subsidy> {

    Optional<Subsidy> findByUuid(UUID uuid);

    /**
     * Every live subsidy of {@code memberId} whose validity window contains
     * {@code on} ({@code valid_from <= on <= valid_until}, or open-ended when
     * {@code valid_until} is null). Backed by the V41 partial index
     * {@code (member_id, valid_from, valid_until) WHERE is_active}.
     */
    @Query("""
            SELECT s FROM Subsidy s
            WHERE s.active = true
              AND s.member.id = :memberId
              AND s.validFrom <= :on
              AND (s.validUntil IS NULL OR s.validUntil >= :on)
            """)
    List<Subsidy> findActiveForMember(@Param("memberId") Long memberId, @Param("on") LocalDate on);

    /** The member's live subsidies (self-service {@code GET /v1/me/subsidies}), newest window first. */
    List<Subsidy> findByMemberIdAndActiveTrueOrderByValidFromDesc(Long memberId);

    /**
     * Solvency sweep helper (vertical-5 #3): of the given candidate members,
     * which ones have a full monthly exoneration active on {@code on}. One query
     * for the whole batch — avoids an N+1 in {@code applyDueTransitions}.
     */
    @Query("""
            SELECT DISTINCT s.member.id FROM Subsidy s
            WHERE s.active = true
              AND s.monthlyPercentage >= 100
              AND s.validFrom <= :on
              AND (s.validUntil IS NULL OR s.validUntil >= :on)
              AND s.member.id IN :memberIds
            """)
    Set<Long> memberIdsWithFullMonthlyExoneration(@Param("memberIds") Collection<Long> memberIds,
                                                  @Param("on") LocalDate on);
}

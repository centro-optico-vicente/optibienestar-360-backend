package com.fenixcore.optibienestar360.modules.promoter.repository;

import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionAwardSettlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface CompetitiveCommissionAwardSettlementRepository extends JpaRepository<CompetitiveCommissionAwardSettlement, Long> {

    Optional<CompetitiveCommissionAwardSettlement> findByUuid(UUID uuid);

    List<CompetitiveCommissionAwardSettlement> findByAward_UuidOrderByCutSequence(UUID awardUuid);

    Optional<CompetitiveCommissionAwardSettlement> findByRule_IdAndPromoter_IdAndPeriodStartAndCutKindAndCutSequence(
            Long ruleId, Long promoterId, LocalDate periodStart,
            CompetitiveCommissionAwardSettlement.CutKind cutKind, int cutSequence);

    /** D14 netting: how much has already been PAID toward this (rule, promoter, period) across all cuts. */
    @Query("""
            SELECT COALESCE(SUM(s.amount), 0) FROM CompetitiveCommissionAwardSettlement s
            WHERE s.rule.id = :ruleId AND s.promoter.id = :promoterId AND s.periodStart = :periodStart
              AND s.active = true AND s.status = 'PAID'
            """)
    BigDecimal sumPaidForRulePromoterPeriod(@Param("ruleId") Long ruleId, @Param("promoterId") Long promoterId,
                                            @Param("periodStart") LocalDate periodStart);

    List<CompetitiveCommissionAwardSettlement> findByAward_IdAndActiveTrueAndStatus(Long awardId, String status);
}

package com.fenixcore.optibienestar360.modules.promoter.repository;

import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionManualDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface CompetitiveCommissionManualDecisionRepository extends JpaRepository<CompetitiveCommissionManualDecision, Long> {

    Optional<CompetitiveCommissionManualDecision> findByUuid(UUID uuid);

    /** The engine reads these as pins/exclusions on every run. */
    List<CompetitiveCommissionManualDecision> findByRule_IdAndPeriodStartAndActiveTrueAndStatus(
            Long ruleId, LocalDate periodStart, String status);

    /** D16 group-wide bans: a DISQUALIFY marked {@code excludeFromGroup} reaches every sibling rule too. */
    List<CompetitiveCommissionManualDecision> findByRule_IdInAndPeriodStartAndActiveTrueAndStatusAndExcludeFromGroupTrue(
            java.util.Collection<Long> ruleIds, LocalDate periodStart, String status);

    /** The winners board card's decision history for one rule + period, most recent first. */
    List<CompetitiveCommissionManualDecision> findByRule_UuidAndPeriodStartOrderByDecidedAtDesc(
            UUID ruleUuid, LocalDate periodStart);
}

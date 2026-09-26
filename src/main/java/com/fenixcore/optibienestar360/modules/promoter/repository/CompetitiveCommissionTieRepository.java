package com.fenixcore.optibienestar360.modules.promoter.repository;

import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionTie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface CompetitiveCommissionTieRepository extends JpaRepository<CompetitiveCommissionTie, Long>,
        JpaSpecificationExecutor<CompetitiveCommissionTie> {

    Optional<CompetitiveCommissionTie> findByUuid(UUID uuid);

    /** The engine's upsert target — at most one per {@code uq_cct_open}. */
    Optional<CompetitiveCommissionTie> findByRule_IdAndPeriodStartAndPositionFromAndActiveTrueAndStatusIn(
            Long ruleId, LocalDate periodStart, int positionFrom, List<String> statuses);

    List<CompetitiveCommissionTie> findByRule_IdAndPeriodStartAndActiveTrueAndStatus(
            Long ruleId, LocalDate periodStart, String status);
}

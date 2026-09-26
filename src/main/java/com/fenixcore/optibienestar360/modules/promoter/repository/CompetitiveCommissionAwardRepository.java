package com.fenixcore.optibienestar360.modules.promoter.repository;

import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionAward;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** List filters (rule/promoter/status/period) go through {@link JpaSpecificationExecutor} — see the service. */
@Transactional(readOnly = true)
public interface CompetitiveCommissionAwardRepository extends JpaRepository<CompetitiveCommissionAward, Long>,
        JpaSpecificationExecutor<CompetitiveCommissionAward> {

    Optional<CompetitiveCommissionAward> findByUuid(UUID uuid);

    /** The reconciliation target: every non-voided award of one rule's period. */
    List<CompetitiveCommissionAward> findByRule_IdAndPeriodStartAndActiveTrueAndStatusNot(
            Long ruleId, LocalDate periodStart, String status);

    /** The confirmation pass's target: every PROVISIONAL award of one rule, any period. */
    List<CompetitiveCommissionAward> findByRule_IdAndActiveTrueAndStatus(Long ruleId, String status);

    /** Usage check for {@code CompetitiveCommissionRulesService.getUsage}/frozen-rule guard. */
    boolean existsByRule_IdAndActiveTrueAndStatusIn(Long ruleId, List<String> statuses);
}

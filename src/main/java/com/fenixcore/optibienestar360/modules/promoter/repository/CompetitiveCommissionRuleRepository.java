package com.fenixcore.optibienestar360.modules.promoter.repository;

import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface CompetitiveCommissionRuleRepository extends JpaRepository<CompetitiveCommissionRule, Long>,
        JpaSpecificationExecutor<CompetitiveCommissionRule> {

    Optional<CompetitiveCommissionRule> findByUuid(UUID uuid);

    /** Every active rule sharing {@code competitionGroup} — used to validate D16's group-mismatch invariant. */
    List<CompetitiveCommissionRule> findByCompetitionGroupAndActiveTrue(String competitionGroup);
}

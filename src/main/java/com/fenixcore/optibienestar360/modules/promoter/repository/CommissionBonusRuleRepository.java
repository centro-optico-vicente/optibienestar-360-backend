package com.fenixcore.optibienestar360.modules.promoter.repository;

import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface CommissionBonusRuleRepository extends JpaRepository<CommissionBonusRule, Long>,
        JpaSpecificationExecutor<CommissionBonusRule> {

    Optional<CommissionBonusRule> findByUuid(UUID uuid);

    /** Every enabled rule — the set the evaluator scans on each run. */
    List<CommissionBonusRule> findByActiveTrue();
}

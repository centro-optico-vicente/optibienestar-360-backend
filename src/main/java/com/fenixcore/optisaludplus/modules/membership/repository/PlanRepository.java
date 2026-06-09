package com.fenixcore.optisaludplus.modules.membership.repository;

import com.fenixcore.optisaludplus.modules.membership.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface PlanRepository extends JpaRepository<Plan, Long>, JpaSpecificationExecutor<Plan> {

    Optional<Plan> findByUuid(UUID uuid);

    /** Natural-key lookup — services that need "the Individual plan" resolve via code, not UUID. */
    Optional<Plan> findByCode(String code);

    boolean existsByCode(String code);
}

package com.fenixcore.optisaludplus.modules.benefit.repository;

import com.fenixcore.optisaludplus.modules.benefit.entity.BenefitUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface BenefitUsageRepository extends JpaRepository<BenefitUsage, Long>,
        JpaSpecificationExecutor<BenefitUsage> {

    Optional<BenefitUsage> findByUuid(UUID uuid);
}

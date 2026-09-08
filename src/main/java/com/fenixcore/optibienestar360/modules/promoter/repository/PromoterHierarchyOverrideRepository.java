package com.fenixcore.optibienestar360.modules.promoter.repository;

import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterHierarchyOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface PromoterHierarchyOverrideRepository extends JpaRepository<PromoterHierarchyOverride, Long>,
        JpaSpecificationExecutor<PromoterHierarchyOverride> {

    Optional<PromoterHierarchyOverride> findByUuid(UUID uuid);

    /** Every override directly funded by a given commission — the cascade-of-rejection entry point (a future PR). */
    List<PromoterHierarchyOverride> findBySourceCommissionId(Long commissionId);

    /** Every override directly funded by another override — recursion step for the same cascade-of-rejection. */
    List<PromoterHierarchyOverride> findBySourceOverrideId(Long overrideId);

    /** Usage check for a future {@code HierarchyOverrideTiersService.countUsages}. */
    long countByTierId(Long tierId);
}

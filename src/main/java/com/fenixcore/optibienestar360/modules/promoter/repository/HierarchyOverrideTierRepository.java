package com.fenixcore.optibienestar360.modules.promoter.repository;

import com.fenixcore.optibienestar360.modules.promoter.entity.HierarchyOverrideTier;
import com.fenixcore.optibienestar360.modules.promoter.entity.HierarchyOverrideTier.OverrideCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface HierarchyOverrideTierRepository extends JpaRepository<HierarchyOverrideTier, Long>,
        JpaSpecificationExecutor<HierarchyOverrideTier> {

    Optional<HierarchyOverrideTier> findByUuid(UUID uuid);

    /**
     * Candidate bands for a (rank, category) combination, highest threshold
     * first — mirrors {@code CommissionTierRepository.findActiveApplicable}'s
     * ordering so {@code HierarchyOverrideService.selectTier} can reuse the
     * exact same "first qualifying wins, threshold 0 is the guaranteed
     * fallback" selection loop.
     */
    @Query("""
            SELECT t FROM HierarchyOverrideTier t
            WHERE t.active = true
              AND t.rank.id = :rankId
              AND t.category = :category
            ORDER BY t.thresholdCount DESC
            """)
    List<HierarchyOverrideTier> findActiveApplicable(@Param("rankId") Long rankId, @Param("category") OverrideCategory category);
}

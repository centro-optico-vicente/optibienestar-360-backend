package com.fenixcore.optibienestar360.modules.promoter.repository;

import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
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

    /** Every band anchored to a campaign — cloned on relaunch by {@code CampaignService}. */
    List<HierarchyOverrideTier> findByCampaign(Campaign campaign);

    /**
     * Every active band, unscoped by rank/category — powers {@code
     * HierarchyOverrideSettlementCutJobRunner} (phase 3 automation), which
     * needs every candidate rule to resolve "is today a cut-close day for
     * this rule" before narrowing to any one beneficiary/category.
     */
    List<HierarchyOverrideTier> findByActiveTrue();

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

package com.fenixcore.optibienestar360.modules.promoter.repository;

import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan.PlanType;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionTier;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionTier.AppliesTo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface CommissionTierRepository extends JpaRepository<CommissionTier, Long>,
        JpaSpecificationExecutor<CommissionTier> {

    Optional<CommissionTier> findByUuid(UUID uuid);

    /** Every tier anchored to a campaign — cloned on relaunch by {@code CampaignService}. */
    List<CommissionTier> findByCampaign(Campaign campaign);

    /**
     * Active tiers applicable to a payment of {@code planType} and fee type
     * {@code appliesTo}, scoped to the promoter's type: rows are either unscoped
     * ({@code promoterType IS NULL}, apply to everyone) or scoped to
     * {@code promoterTypeId} — rows scoped to a *different* promoter type never
     * qualify and are excluded. Ordered so a promoter-type-specific match always
     * outranks a generic one (project chat 2026-08-07), then highest-threshold
     * first so the engine can pick the top qualifying tier by iterating within
     * that precedence group. Ties broken by id for determinism.
     */
    @Query("""
            SELECT t FROM CommissionTier t
            WHERE t.active = true
              AND (t.planType = :planType OR t.planType IS NULL)
              AND (t.appliesTo = :appliesTo OR t.appliesTo = :both)
              AND (t.promoterType IS NULL OR (:promoterTypeId IS NOT NULL AND t.promoterType.id = :promoterTypeId))
            ORDER BY (CASE WHEN t.promoterType IS NOT NULL THEN 0 ELSE 1 END), t.thresholdCount DESC, t.id ASC
            """)
    List<CommissionTier> findActiveApplicable(@Param("planType") PlanType planType,
                                              @Param("appliesTo") AppliesTo appliesTo,
                                              @Param("both") AppliesTo both,
                                              @Param("promoterTypeId") Long promoterTypeId);
}

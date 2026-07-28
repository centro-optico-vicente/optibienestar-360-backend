package com.fenixcore.optibienestar360.modules.promoter.repository;

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

    /**
     * Active tiers applicable to a payment of {@code planType} and fee type
     * {@code appliesTo}: those scoped to that plan (or unscoped) and to that fee
     * type (or BOTH), ordered highest-threshold first so the engine can pick the
     * top qualifying tier by iterating. Ties broken by id for determinism.
     */
    @Query("""
            SELECT t FROM CommissionTier t
            WHERE t.active = true
              AND (t.planType = :planType OR t.planType IS NULL)
              AND (t.appliesTo = :appliesTo OR t.appliesTo = :both)
            ORDER BY t.thresholdCount DESC, t.id ASC
            """)
    List<CommissionTier> findActiveApplicable(@Param("planType") PlanType planType,
                                              @Param("appliesTo") AppliesTo appliesTo,
                                              @Param("both") AppliesTo both);
}

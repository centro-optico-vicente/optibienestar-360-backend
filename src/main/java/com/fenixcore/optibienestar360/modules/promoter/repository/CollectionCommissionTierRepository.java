package com.fenixcore.optibienestar360.modules.promoter.repository;

import com.fenixcore.optibienestar360.modules.promoter.entity.CollectionCommissionTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface CollectionCommissionTierRepository extends JpaRepository<CollectionCommissionTier, Long>,
        JpaSpecificationExecutor<CollectionCommissionTier> {

    Optional<CollectionCommissionTier> findByUuid(UUID uuid);

    /**
     * Active buckets that cover {@code days} and are scoped to the promoter's
     * type: unscoped rows ({@code promoterType IS NULL}, apply to everyone) or
     * rows scoped to {@code promoterTypeId} — rows scoped to a *different*
     * promoter type are excluded. Ordered so a promoter-type-specific match
     * always outranks a generic one (project chat 2026-08-07), then smallest
     * {@code maxDays} first, so the engine can pick the top qualifying bucket by
     * taking the first result.
     */
    @Query("""
            SELECT t FROM CollectionCommissionTier t
            WHERE t.active = true
              AND t.maxDays >= :days
              AND (t.promoterType IS NULL OR (:promoterTypeId IS NOT NULL AND t.promoterType.id = :promoterTypeId))
            ORDER BY (CASE WHEN t.promoterType IS NOT NULL THEN 0 ELSE 1 END), t.maxDays ASC, t.id ASC
            """)
    List<CollectionCommissionTier> findActiveApplicable(@Param("days") int days,
                                                        @Param("promoterTypeId") Long promoterTypeId);
}

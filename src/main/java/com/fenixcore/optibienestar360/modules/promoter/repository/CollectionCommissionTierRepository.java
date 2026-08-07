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
     * Active buckets that cover {@code days}, smallest {@code maxDays} first so
     * the engine can pick the top qualifying bucket by taking the first result.
     */
    @Query("""
            SELECT t FROM CollectionCommissionTier t
            WHERE t.active = true
              AND t.maxDays >= :days
            ORDER BY t.maxDays ASC, t.id ASC
            """)
    List<CollectionCommissionTier> findActiveApplicable(@Param("days") int days);
}

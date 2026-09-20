package com.fenixcore.optibienestar360.modules.promoter.repository;

import com.fenixcore.optibienestar360.modules.promoter.entity.CollectionCommissionTier;
import com.fenixcore.optibienestar360.modules.promoter.entity.CollectionCommissionTier.Basis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
              AND t.basis = :basis
              AND t.maxDays >= :days
              AND (t.promoterType IS NULL OR (:promoterTypeId IS NOT NULL AND t.promoterType.id = :promoterTypeId))
            ORDER BY (CASE WHEN t.promoterType IS NOT NULL THEN 0 ELSE 1 END), t.maxDays ASC, t.id ASC
            """)
    List<CollectionCommissionTier> findActiveApplicableInternal(@Param("days") int days,
                                                        @Param("promoterTypeId") Long promoterTypeId,
                                                        @Param("basis") Basis basis);

    /** Convenience overload — always looks up {@code basis=DAYS} buckets, same as before V126. */
    default List<CollectionCommissionTier> findActiveApplicable(int days, Long promoterTypeId) {
        return findActiveApplicableInternal(days, promoterTypeId, Basis.DAYS);
    }

    @Query("""
            SELECT t FROM CollectionCommissionTier t
            WHERE t.active = true
              AND t.basis = :basis
              AND t.maxAmount >= :amount
              AND (t.promoterType IS NULL OR (:promoterTypeId IS NOT NULL AND t.promoterType.id = :promoterTypeId))
            ORDER BY (CASE WHEN t.promoterType IS NOT NULL THEN 0 ELSE 1 END), t.maxAmount ASC, t.id ASC
            """)
    List<CollectionCommissionTier> findActiveApplicableByAmountInternal(@Param("amount") BigDecimal amount,
                                                                 @Param("promoterTypeId") Long promoterTypeId,
                                                                 @Param("basis") Basis basis);

    /**
     * {@code basis=AMOUNT} counterpart of {@link #findActiveApplicable(int, Long)}
     * — same active/promoter-type-scope/ordering semantics, keyed on the
     * ascending {@code maxAmount} bucket instead of {@code maxDays}.
     */
    default List<CollectionCommissionTier> findActiveApplicableByAmount(BigDecimal amount, Long promoterTypeId) {
        return findActiveApplicableByAmountInternal(amount, promoterTypeId, Basis.AMOUNT);
    }
}

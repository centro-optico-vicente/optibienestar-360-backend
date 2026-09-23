package com.fenixcore.optibienestar360.modules.promoter.repository;

import com.fenixcore.optibienestar360.modules.promoter.entity.CollectionCommissionTier;
import com.fenixcore.optibienestar360.modules.promoter.entity.CollectionCommissionTier.Basis;
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
     * Every active tier, unscoped by basis/promoter type — powers {@code
     * CollectionCommissionTierSettlementCutJobRunner} (phase 3 automation,
     * cobranza gap), which needs every candidate rule to resolve "is today a
     * cut-close day for this rule" before narrowing to any one promoter.
     * Same pattern as {@code CommissionTierRepository#findByActiveTrue} /
     * {@code HierarchyOverrideTierRepository#findByActiveTrue}.
     */
    List<CollectionCommissionTier> findByActiveTrue();

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
            SELECT DISTINCT t FROM CollectionCommissionTier t
            LEFT JOIN t.promoterTypes pt
            WHERE t.active = true
              AND t.basis = :basis
              AND t.maxDays >= :days
              AND (t.promoterTypes IS EMPTY OR (:promoterTypeId IS NOT NULL AND pt.id = :promoterTypeId))
            ORDER BY (CASE WHEN pt IS NOT NULL THEN 0 ELSE 1 END), t.maxDays ASC, t.id ASC
            """)
    List<CollectionCommissionTier> findActiveApplicableInternal(@Param("days") int days,
                                                        @Param("promoterTypeId") Long promoterTypeId,
                                                        @Param("basis") Basis basis);

    /** Convenience overload — always looks up {@code basis=DAYS} buckets, same as before V126. */
    default List<CollectionCommissionTier> findActiveApplicable(int days, Long promoterTypeId) {
        return findActiveApplicableInternal(days, promoterTypeId, Basis.DAYS);
    }

    /**
     * All active {@code basis=AMOUNT} buckets scoped to the promoter's type
     * (V144). Unlike {@link #findActiveApplicableInternal}, this does NOT
     * filter by the collected amount in SQL — {@code minAmount} is a
     * threshold compared against an amount that may need currency conversion
     * per-tier first (each tier's own {@code minAmountCurrency}), so the
     * caller ({@code CommissionService}) converts and filters in Java.
     * Ordered so a promoter-type-specific match always outranks a generic
     * one (same priority as {@link #findActiveApplicableInternal}), then
     * descending {@code minAmount} first, so the caller can pick the highest
     * bucket whose (converted) threshold the amount reaches or exceeds by
     * taking the first qualifying row in iteration order.
     */
    @Query("""
            SELECT DISTINCT t FROM CollectionCommissionTier t
            LEFT JOIN t.promoterTypes pt
            WHERE t.active = true
              AND t.basis = :basis
              AND (t.promoterTypes IS EMPTY OR (:promoterTypeId IS NOT NULL AND pt.id = :promoterTypeId))
            ORDER BY (CASE WHEN pt IS NOT NULL THEN 0 ELSE 1 END), t.minAmount DESC, t.id ASC
            """)
    List<CollectionCommissionTier> findActiveApplicableByAmountInternal(@Param("promoterTypeId") Long promoterTypeId,
                                                                 @Param("basis") Basis basis);

    /**
     * {@code basis=AMOUNT} counterpart of {@link #findActiveApplicable(int, Long)}
     * — same active/promoter-type-scope/ordering priority, but returns every
     * candidate (descending {@code minAmount}) for the caller to convert +
     * threshold-filter (V144 minimum-threshold semantics), instead of
     * filtering by amount in SQL.
     */
    default List<CollectionCommissionTier> findActiveApplicableByAmount(Long promoterTypeId) {
        return findActiveApplicableByAmountInternal(promoterTypeId, Basis.AMOUNT);
    }
}

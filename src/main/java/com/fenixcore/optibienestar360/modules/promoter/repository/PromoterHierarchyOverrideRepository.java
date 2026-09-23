package com.fenixcore.optibienestar360.modules.promoter.repository;

import com.fenixcore.optibienestar360.modules.promoter.entity.HierarchyOverrideTier.OverrideCategory;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterHierarchyOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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

    /**
     * Powers {@code HierarchyOverrideReRatingService} (month-close, PR3) —
     * every PENDING override whose period falls inside the requested range,
     * across every beneficiary. Mirrors {@code CommissionRepository
     * .findPendingForPeriod}.
     */
    @Query("""
            SELECT o FROM PromoterHierarchyOverride o
            WHERE o.active = true
              AND o.status = 'PENDING'
              AND o.periodStart >= :periodStart
              AND o.periodEnd   <= :periodEnd
            ORDER BY o.promoter.id, o.earnedAt
            """)
    List<PromoterHierarchyOverride> findPendingForPeriod(
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd);

    /**
     * Powers {@code CommissionRetroactiveTopUpService} (V105, PR4) — every
     * PAID override whose period falls inside the settlement range. Mirrors
     * {@link #findPendingForPeriod}.
     */
    @Query("""
            SELECT o FROM PromoterHierarchyOverride o
            WHERE o.active = true
              AND o.status = 'PAID'
              AND o.periodStart >= :periodStart
              AND o.periodEnd   <= :periodEnd
            ORDER BY o.promoter.id, o.earnedAt
            """)
    List<PromoterHierarchyOverride> findPaidForPeriod(
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd);

    /**
     * Powers {@code HierarchyOverridePeriodicSettlementService} (V112, hub
     * plan §3) — every PENDING override of a single beneficiary/category
     * whose period falls inside a single <b>cut</b> (the caller passes the
     * {@code PeriodCutCalculator} cut bounds, not the whole settlement
     * window). Still {@code PENDING} at this point — like {@code
     * CommissionPayoutService}, the caller must additionally confirm the
     * root commission is {@code APPROVED} before disbursing (overrides carry
     * no approval state of their own).
     */
    @Query("""
            SELECT o FROM PromoterHierarchyOverride o
            WHERE o.active = true
              AND o.status = 'PENDING'
              AND o.promoter.id = :promoterId
              AND o.category = :category
              AND o.periodStart >= :cutStart
              AND o.periodEnd   <= :cutEnd
            ORDER BY o.earnedAt
            """)
    List<PromoterHierarchyOverride> findPendingForPromoterCategoryInPeriod(
            @Param("promoterId") Long promoterId,
            @Param("category") OverrideCategory category,
            @Param("cutStart") LocalDate cutStart,
            @Param("cutEnd") LocalDate cutEnd);

    /**
     * Powers {@code CommissionRetroactiveTopUpService#executeCut} (Fase A,
     * retroactive settlement axis) — every PAID override of a single
     * beneficiary/category whose period falls inside an arbitrary
     * rule-derived window (the accrual window's start through the current
     * retroactive cut's end). Mirrors {@link #findPaidForPeriod} but
     * beneficiary-scoped, the same way {@link #findPendingForPromoterCategoryInPeriod}
     * mirrors {@link #findPendingForPeriod}.
     */
    @Query("""
            SELECT o FROM PromoterHierarchyOverride o
            WHERE o.active = true
              AND o.status = 'PAID'
              AND o.promoter.id = :promoterId
              AND o.category = :category
              AND o.periodStart >= :start
              AND o.periodEnd   <= :end
            ORDER BY o.earnedAt
            """)
    List<PromoterHierarchyOverride> findPaidForPromoterCategoryInPeriod(
            @Param("promoterId") Long promoterId,
            @Param("category") OverrideCategory category,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);
}

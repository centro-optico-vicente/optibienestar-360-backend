package com.fenixcore.optibienestar360.modules.promoter.repository;

import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionRetroactiveTopUp;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionRetroactiveTopUp.LedgerType;
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
public interface CommissionRetroactiveTopUpRepository extends JpaRepository<CommissionRetroactiveTopUp, Long>,
        JpaSpecificationExecutor<CommissionRetroactiveTopUp> {

    Optional<CommissionRetroactiveTopUp> findByUuid(UUID uuid);

    /**
     * Idempotency lookup — the V105 unique constraint's own key. The
     * settlement-close service upserts by this instead of blindly inserting,
     * so re-running the close for an already-closed period never
     * double-counts.
     */
    Optional<CommissionRetroactiveTopUp> findByPromoterIdAndLedgerTypeAndPeriodStartAndPeriodEnd(
            Long promoterId, LedgerType ledgerType, LocalDate periodStart, LocalDate periodEnd);

    /**
     * Powers the payout consolidation (§3 last bullet) — every PENDING
     * top-up whose period falls inside the requested range. Mirrors {@code
     * CommissionRepository.findPendingForPeriod}.
     */
    @Query("""
            SELECT t FROM CommissionRetroactiveTopUp t
            WHERE t.active = true
              AND t.status = 'PENDING'
              AND t.periodStart >= :periodStart
              AND t.periodEnd   <= :periodEnd
            ORDER BY t.promoter.id, t.createdAt
            """)
    List<CommissionRetroactiveTopUp> findPendingForPeriod(
            @Param("periodStart") LocalDate periodStart, @Param("periodEnd") LocalDate periodEnd);
}

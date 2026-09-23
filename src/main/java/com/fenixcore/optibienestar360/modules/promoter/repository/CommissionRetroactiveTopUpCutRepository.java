package com.fenixcore.optibienestar360.modules.promoter.repository;

import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionRetroactiveTopUp.LedgerType;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionRetroactiveTopUpCut;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface CommissionRetroactiveTopUpCutRepository extends JpaRepository<CommissionRetroactiveTopUpCut, Long>,
        JpaSpecificationExecutor<CommissionRetroactiveTopUpCut> {

    Optional<CommissionRetroactiveTopUpCut> findByUuid(UUID uuid);

    /**
     * Idempotency lookup — the V150 unique constraint's own key. {@code
     * executeCut} upserts by this instead of blindly inserting, so
     * re-running the same cut never double-counts.
     */
    Optional<CommissionRetroactiveTopUpCut> findByPromoterIdAndLedgerTypeAndAccrualPeriodStartAndAccrualPeriodEndAndCutSequence(
            Long promoterId, LedgerType ledgerType, LocalDate accrualPeriodStart, LocalDate accrualPeriodEnd, int cutSequence);

    /**
     * Sum of {@code retro_amount} of every {@code PAID} cut of the same
     * (promoter, ledger, accrual period) whose {@code cut_sequence} is
     * strictly earlier than the one being computed — the netting term that
     * keeps a later, higher-band cut from double-paying what an earlier
     * cut's retroactive already disbursed. {@code COALESCE} keeps it {@code
     * 0} (never null) when there is no earlier PAID cut yet.
     */
    @Query("""
            SELECT COALESCE(SUM(c.retroAmount), 0) FROM CommissionRetroactiveTopUpCut c
            WHERE c.active = true
              AND c.status = 'PAID'
              AND c.promoter.id = :promoterId
              AND c.ledgerType = :ledgerType
              AND c.accrualPeriodStart = :accrualPeriodStart
              AND c.accrualPeriodEnd = :accrualPeriodEnd
              AND c.cutSequence < :cutSequence
            """)
    BigDecimal sumPaidRetroBeforeSequence(
            @Param("promoterId") Long promoterId,
            @Param("ledgerType") LedgerType ledgerType,
            @Param("accrualPeriodStart") LocalDate accrualPeriodStart,
            @Param("accrualPeriodEnd") LocalDate accrualPeriodEnd,
            @Param("cutSequence") int cutSequence);
}

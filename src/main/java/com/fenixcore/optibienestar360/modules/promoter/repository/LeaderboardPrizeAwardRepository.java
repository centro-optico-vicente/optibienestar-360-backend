package com.fenixcore.optibienestar360.modules.promoter.repository;

import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;
import com.fenixcore.optibienestar360.modules.promoter.entity.LeaderboardPrizeAward;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface LeaderboardPrizeAwardRepository extends JpaRepository<LeaderboardPrizeAward, Long>,
        JpaSpecificationExecutor<LeaderboardPrizeAward> {

    Optional<LeaderboardPrizeAward> findByUuid(UUID uuid);

    /** Usage check for {@code PromotersService.countUsages} — ALL rows (active + inactive). */
    long countByPromoterId(Long promoterId);

    /** Idempotency guard for the period-close awarding — one award per (promoter, period, rank). */
    boolean existsByPromoterIdAndPeriodStrategyAndPeriodStartAndPeriodEndAndRankAndActiveTrue(
            Long promoterId, PeriodStrategy periodStrategy, LocalDate periodStart, LocalDate periodEnd, int rank);
}

package com.fenixcore.optibienestar360.modules.promoter.repository;

import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;
import com.fenixcore.optibienestar360.modules.promoter.entity.LeaderboardPrize;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface LeaderboardPrizeRepository extends JpaRepository<LeaderboardPrize, Long>,
        JpaSpecificationExecutor<LeaderboardPrize> {

    Optional<LeaderboardPrize> findByUuid(UUID uuid);

    /** Active prizes configured for a strategy (rank → amount), for display + awarding. */
    List<LeaderboardPrize> findByPeriodStrategyAndActiveTrue(PeriodStrategy periodStrategy);

    /** Every active prize config — the runner derives the distinct strategies to close. */
    List<LeaderboardPrize> findByActiveTrue();

    /** Duplicate guard for config create — one active prize per (rank, strategy). */
    boolean existsByRankAndPeriodStrategyAndActiveTrue(int rank, PeriodStrategy periodStrategy);
}

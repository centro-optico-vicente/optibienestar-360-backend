package com.fenixcore.optibienestar360.modules.promoter.repository;

import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionPeriodSummary;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionPeriodSummaryId;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Read-only access to the {@code commission_period_summary} view. Extends the
 * base {@link Repository} (not {@code JpaRepository}) — the view is queryable but
 * never persisted.
 */
@Transactional(readOnly = true)
public interface CommissionPeriodSummaryRepository
        extends Repository<CommissionPeriodSummary, CommissionPeriodSummaryId> {

    /** Every promoter's aggregate for a specific period — the leaderboard's raw input. */
    List<CommissionPeriodSummary> findByPeriodStrategyAndPeriodStartAndPeriodEnd(
            String periodStrategy, LocalDate periodStart, LocalDate periodEnd);

    /** One promoter's monthly history, most recent period first — the admin detail's commission tab. */
    List<CommissionPeriodSummary> findByPromoterIdOrderByPeriodStartDesc(Long promoterId);
}

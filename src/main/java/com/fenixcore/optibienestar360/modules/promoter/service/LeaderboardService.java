package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.util.PeriodStrategies;
import com.fenixcore.optibienestar360.modules.promoter.dto.LeaderboardDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.LeaderboardEntryDto;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionPeriodSummary;
import com.fenixcore.optibienestar360.modules.promoter.entity.LeaderboardPrize;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionPeriodSummaryRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.LeaderboardPrizeRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ranks promoters by commission earnings within a period (v2 PDF #5, V42).
 * Reads the {@code commission_period_summary} view, excludes system promoters
 * (the INSTITUCION row never competes in the public ranking — PDF #5), sorts by
 * total earnings, and assigns dense ranks. The same {@link #rank} method feeds
 * both the read endpoint and the period-close prize awarding, so a promoter's
 * displayed rank and their prize are always computed identically.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaderboardService {

    private final CommissionPeriodSummaryRepository summaryRepository;
    private final PromoterRepository promoterRepository;
    private final LeaderboardPrizeRepository prizeRepository;

    /** A promoter's place in a period's ranking. */
    public record RankedEntry(int rank, Promoter promoter, BigDecimal totalCommission, long commissionCount) {}

    /**
     * Ranked promoters for the window, top {@code limit} first. Excludes
     * system + inactive promoters. Ties broken by commission count, then name,
     * for a stable order.
     */
    public List<RankedEntry> rank(PeriodStrategy strategy, PeriodStrategies.Window window, int limit) {
        List<CommissionPeriodSummary> rows = summaryRepository
                .findByPeriodStrategyAndPeriodStartAndPeriodEnd(strategy.name(), window.start(), window.end());
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<Long, Promoter> promoters = promoterRepository
                .findAllById(rows.stream().map(CommissionPeriodSummary::getPromoterId).toList()).stream()
                .collect(Collectors.toMap(Promoter::getId, Function.identity()));

        record Row(Promoter promoter, BigDecimal total, long count) {}
        List<Row> ranked = new ArrayList<>();
        for (CommissionPeriodSummary s : rows) {
            Promoter p = promoters.get(s.getPromoterId());
            if (p == null || !p.isActive() || p.isSystem()) {
                continue;   // system row never competes on the public leaderboard
            }
            ranked.add(new Row(p, s.getTotalAmount() != null ? s.getTotalAmount() : BigDecimal.ZERO,
                    s.getCommissionCount()));
        }

        ranked.sort(Comparator
                .comparing(Row::total).reversed()
                .thenComparing(Comparator.comparingLong(Row::count).reversed())
                .thenComparing(r -> r.promoter().getDisplayName(), Comparator.nullsLast(Comparator.naturalOrder())));

        List<RankedEntry> result = new ArrayList<>();
        int max = Math.min(limit, ranked.size());
        for (int i = 0; i < max; i++) {
            Row r = ranked.get(i);
            result.add(new RankedEntry(i + 1, r.promoter(), r.total(), r.count()));
        }
        return result;
    }

    /** The public leaderboard DTO for a resolved period, with prizes attached. */
    public LeaderboardDto leaderboard(PeriodStrategy strategy, java.time.LocalDate reference, int limit) {
        PeriodStrategies.Window window = PeriodStrategies.window(strategy.name(), reference);
        List<RankedEntry> ranked = rank(strategy, window, limit);

        Map<Integer, LeaderboardPrize> prizesByRank = prizeRepository
                .findByPeriodStrategyAndActiveTrue(strategy).stream()
                .collect(Collectors.toMap(LeaderboardPrize::getRank, Function.identity(), (a, b) -> a));

        List<LeaderboardEntryDto> entries = ranked.stream().map(e -> {
            LeaderboardPrize prize = prizesByRank.get(e.rank());
            return new LeaderboardEntryDto(
                    e.rank(), e.promoter().getUuid(), e.promoter().getDisplayName(),
                    e.promoter().getReferralCode(), e.totalCommission(), e.commissionCount(),
                    prize != null ? prize.getPrizeAmount() : null,
                    prize != null ? prize.getPrizeCurrency() : null);
        }).toList();

        return new LeaderboardDto(strategy, window.start(), window.end(), entries);
    }
}

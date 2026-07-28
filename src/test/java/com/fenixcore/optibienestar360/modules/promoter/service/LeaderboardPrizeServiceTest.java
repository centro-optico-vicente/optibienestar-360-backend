package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.promoter.dto.PrizeAwardResult;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;
import com.fenixcore.optibienestar360.modules.promoter.entity.LeaderboardPrize;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.LeaderboardPrizeAwardRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.LeaderboardPrizeRepository;
import com.fenixcore.optibienestar360.modules.promoter.service.LeaderboardService.RankedEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaderboardPrizeServiceTest {

    @Mock private LeaderboardPrizeRepository prizeRepository;
    @Mock private LeaderboardPrizeAwardRepository awardRepository;
    @Mock private LeaderboardService leaderboardService;

    private LeaderboardPrizeService sut() {
        return new LeaderboardPrizeService(prizeRepository, awardRepository, leaderboardService);
    }

    private static final LocalDate REF = LocalDate.of(2026, 6, 15);

    @Test
    void award_grantsConfiguredPrizeToRankedPromoter() {
        Promoter winner = promoter(2L);
        when(prizeRepository.findByPeriodStrategyAndActiveTrue(PeriodStrategy.MONTHLY))
                .thenReturn(List.of(prize(1, "100")));
        when(leaderboardService.rank(eq(PeriodStrategy.MONTHLY), any(), eq(1)))
                .thenReturn(List.of(new RankedEntry(1, winner, new BigDecimal("500"), 5L)));
        when(awardRepository.existsByPromoterIdAndPeriodStrategyAndPeriodStartAndPeriodEndAndRankAndActiveTrue(
                anyLong(), any(), any(), any(), anyInt())).thenReturn(false);

        PrizeAwardResult result = sut().award(PeriodStrategy.MONTHLY, REF, false);

        assertThat(result.awardsCreated()).isEqualTo(1);
        assertThat(result.totalAmount()).isEqualByComparingTo("100");
        verify(awardRepository).save(any());
    }

    @Test
    void award_dryRun_doesNotPersist() {
        Promoter winner = promoter(2L);
        when(prizeRepository.findByPeriodStrategyAndActiveTrue(PeriodStrategy.MONTHLY))
                .thenReturn(List.of(prize(1, "100")));
        when(leaderboardService.rank(eq(PeriodStrategy.MONTHLY), any(), eq(1)))
                .thenReturn(List.of(new RankedEntry(1, winner, new BigDecimal("500"), 5L)));
        when(awardRepository.existsByPromoterIdAndPeriodStrategyAndPeriodStartAndPeriodEndAndRankAndActiveTrue(
                anyLong(), any(), any(), any(), anyInt())).thenReturn(false);

        PrizeAwardResult result = sut().award(PeriodStrategy.MONTHLY, REF, true);

        assertThat(result.awardsCreated()).isEqualTo(1);
        verify(awardRepository, never()).save(any());
    }

    @Test
    void award_noPrizesConfigured_isNoop() {
        when(prizeRepository.findByPeriodStrategyAndActiveTrue(PeriodStrategy.MONTHLY)).thenReturn(List.of());

        PrizeAwardResult result = sut().award(PeriodStrategy.MONTHLY, REF, false);

        assertThat(result.awardsCreated()).isZero();
        verify(leaderboardService, never()).rank(any(), any(), anyInt());
    }

    @Test
    void award_idempotent_skipsAlreadyAwarded() {
        Promoter winner = promoter(2L);
        when(prizeRepository.findByPeriodStrategyAndActiveTrue(PeriodStrategy.MONTHLY))
                .thenReturn(List.of(prize(1, "100")));
        when(leaderboardService.rank(eq(PeriodStrategy.MONTHLY), any(), eq(1)))
                .thenReturn(List.of(new RankedEntry(1, winner, new BigDecimal("500"), 5L)));
        when(awardRepository.existsByPromoterIdAndPeriodStrategyAndPeriodStartAndPeriodEndAndRankAndActiveTrue(
                anyLong(), any(), any(), any(), anyInt())).thenReturn(true);

        PrizeAwardResult result = sut().award(PeriodStrategy.MONTHLY, REF, false);

        assertThat(result.awardsCreated()).isZero();
        verify(awardRepository, never()).save(any());
    }

    private static LeaderboardPrize prize(int rank, String amount) {
        LeaderboardPrize p = new LeaderboardPrize();
        p.setRank(rank);
        p.setPeriodStrategy(PeriodStrategy.MONTHLY);
        p.setPrizeAmount(new BigDecimal(amount));
        p.setPrizeCurrency("USD");
        return p;
    }

    private static Promoter promoter(long id) {
        Promoter p = new Promoter();
        p.setId(id);
        p.setDisplayName("P" + id);
        p.setReferralCode("P" + id);
        p.setActive(true);
        return p;
    }
}

package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.util.PeriodStrategies;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionPeriodSummary;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionPeriodSummaryRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.LeaderboardPrizeRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import com.fenixcore.optibienestar360.modules.promoter.service.LeaderboardService.RankedEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaderboardServiceTest {

    @Mock private CommissionPeriodSummaryRepository summaryRepository;
    @Mock private PromoterRepository promoterRepository;
    @Mock private LeaderboardPrizeRepository prizeRepository;

    @InjectMocks private LeaderboardService service;

    private static final PeriodStrategies.Window WINDOW =
            new PeriodStrategies.Window(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

    @Test
    void rank_excludesSystem_andSortsByTotalDesc() {
        Promoter system = promoter(1L, "INSTITUCION", true);
        Promoter top = promoter(2L, "Ana", false);
        Promoter second = promoter(3L, "Beto", false);

        // Build the mock summaries first — stubbing them inside List.of() while the
        // repository stubbing is open would trip Mockito's UnfinishedStubbing check.
        List<CommissionPeriodSummary> rows = List.of(summary(1L, "999"), summary(2L, "100"), summary(3L, "50"));
        when(summaryRepository.findByPeriodStrategyAndPeriodStartAndPeriodEnd("MONTHLY", WINDOW.start(), WINDOW.end()))
                .thenReturn(rows);
        when(promoterRepository.findAllById(any())).thenReturn(List.of(system, top, second));

        List<RankedEntry> ranked = service.rank(PeriodStrategy.MONTHLY, WINDOW, 10);

        assertThat(ranked).hasSize(2);   // system excluded
        assertThat(ranked.get(0).rank()).isEqualTo(1);
        assertThat(ranked.get(0).promoter()).isEqualTo(top);
        assertThat(ranked.get(1).promoter()).isEqualTo(second);
    }

    @Test
    void rank_honorsLimit() {
        Promoter a = promoter(2L, "Ana", false);
        Promoter b = promoter(3L, "Beto", false);
        List<CommissionPeriodSummary> rows = List.of(summary(2L, "100"), summary(3L, "50"));
        when(summaryRepository.findByPeriodStrategyAndPeriodStartAndPeriodEnd("MONTHLY", WINDOW.start(), WINDOW.end()))
                .thenReturn(rows);
        when(promoterRepository.findAllById(any())).thenReturn(List.of(a, b));

        assertThat(service.rank(PeriodStrategy.MONTHLY, WINDOW, 1)).hasSize(1);
    }

    @Test
    void rank_emptyWhenNoRows() {
        when(summaryRepository.findByPeriodStrategyAndPeriodStartAndPeriodEnd("MONTHLY", WINDOW.start(), WINDOW.end()))
                .thenReturn(List.of());

        assertThat(service.rank(PeriodStrategy.MONTHLY, WINDOW, 10)).isEmpty();
    }

    private static CommissionPeriodSummary summary(long promoterId, String total) {
        CommissionPeriodSummary s = mock(CommissionPeriodSummary.class);
        when(s.getPromoterId()).thenReturn(promoterId);
        lenient().when(s.getTotalAmount()).thenReturn(new BigDecimal(total));
        lenient().when(s.getCommissionCount()).thenReturn(1L);
        return s;
    }

    private static Promoter promoter(long id, String name, boolean system) {
        Promoter p = new Promoter();
        p.setId(id);
        p.setDisplayName(name);
        p.setReferralCode(name);
        p.setSystem(system);
        p.setActive(true);
        return p;
    }
}

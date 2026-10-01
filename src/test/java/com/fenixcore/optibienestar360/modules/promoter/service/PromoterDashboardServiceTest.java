package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterDashboardDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterMemberRow;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.AchievementDateBasis;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.CompetitionType;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.CompetitiveMetric;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.PeriodAxisStrategy;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.metric.CompetitiveMetricProvider;
import com.fenixcore.optibienestar360.modules.promoter.metric.CompetitiveRankingEngine.Candidate;
import com.fenixcore.optibienestar360.modules.promoter.metric.MetricScope;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CompetitiveCommissionRuleRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import com.fenixcore.optibienestar360.core.util.PeriodStrategies;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.lenient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PromoterDashboardService} — the {@code /v1/promoter/me}
 * aggregation (v2 PDF #4). Locks the collection-health bucketing (al día /
 * vencida / sin membresía) and the not-a-promoter 404.
 */
@ExtendWith(MockitoExtension.class)
class PromoterDashboardServiceTest {

    @Mock private PromoterRepository promoterRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private CommissionRepository commissionRepository;
    @Mock private CompetitiveCommissionRuleRepository competitiveRuleRepository;

    private PromoterDashboardService service() {
        // No competitive RANKING/COMMISSION_EARNED rule configured — leaderboardPosition
        // stays null, same as every test below already expects.
        lenient().when(competitiveRuleRepository.findByActiveTrue()).thenReturn(List.of());
        return new PromoterDashboardService(promoterRepository, memberRepository, commissionRepository,
                competitiveRuleRepository, List.of());
    }

    @Test
    void bucketsPortfolioByMembershipStatus_andReportsPeriodCommissions() {
        UUID userUuid = UUID.randomUUID();
        Promoter promoter = promoter();
        when(promoterRepository.findActiveByUserUuid(userUuid)).thenReturn(Optional.of(promoter));
        when(memberRepository.findPromoterPortfolio(promoter.getId())).thenReturn(List.of(
                row("ACTIVE"),
                row("SUSPENDED"),
                row("EXPIRED"),
                row(null)  // enrolled, no active membership
        ));
        when(commissionRepository.sumForPromoterInPeriod(eq(promoter.getId()), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new BigDecimal("42.00"));

        PromoterDashboardDto dto = service().getMyDashboard(userUuid);

        assertThat(dto.activeAffiliates()).isEqualTo(4);
        assertThat(dto.affiliatesUpToDate()).isEqualTo(1);
        assertThat(dto.affiliatesOverdue()).isEqualTo(2);       // SUSPENDED + EXPIRED
        assertThat(dto.affiliatesWithoutMembership()).isEqualTo(1);
        assertThat(dto.periodCommissions()).isEqualByComparingTo("42.00");
        assertThat(dto.periodCurrency()).isEqualTo("USD");
        assertThat(dto.leaderboardPosition()).isNull();          // PDF #5, not built
        assertThat(dto.portfolio()).hasSize(4);
        // The queried period is the current calendar month.
        LocalDate today = LocalDate.now();
        assertThat(dto.periodStart()).isEqualTo(today.withDayOfMonth(1));
        assertThat(dto.periodEnd()).isEqualTo(today.withDayOfMonth(today.lengthOfMonth()));
    }

    @Test
    void emptyPortfolio_yieldsZeroedBuckets() {
        UUID userUuid = UUID.randomUUID();
        Promoter promoter = promoter();
        when(promoterRepository.findActiveByUserUuid(userUuid)).thenReturn(Optional.of(promoter));
        when(memberRepository.findPromoterPortfolio(promoter.getId())).thenReturn(List.of());
        when(commissionRepository.sumForPromoterInPeriod(any(), any(), any())).thenReturn(BigDecimal.ZERO);

        PromoterDashboardDto dto = service().getMyDashboard(userUuid);

        assertThat(dto.activeAffiliates()).isZero();
        assertThat(dto.affiliatesUpToDate()).isZero();
        assertThat(dto.affiliatesOverdue()).isZero();
        assertThat(dto.affiliatesWithoutMembership()).isZero();
        assertThat(dto.portfolio()).isEmpty();
    }

    @Test
    void notAPromoter_yields404_andNeverQueriesPortfolio() {
        UUID userUuid = UUID.randomUUID();
        when(promoterRepository.findActiveByUserUuid(userUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getMyDashboard(userUuid))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("me.promoter.not_found");
        verify(memberRepository, never()).findPromoterPortfolio(any());
    }

    // ─── Leaderboard position (Fase 6) ──────────────────────────────────────

    @Test
    void leaderboardPosition_ranksPromoterAgainstTheMigratedRankingRule() {
        UUID userUuid = UUID.randomUUID();
        Promoter promoter = promoter();
        when(promoterRepository.findActiveByUserUuid(userUuid)).thenReturn(Optional.of(promoter));
        when(memberRepository.findPromoterPortfolio(promoter.getId())).thenReturn(List.of());
        when(commissionRepository.sumForPromoterInPeriod(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(competitiveRuleRepository.findByActiveTrue()).thenReturn(List.of(rankingRule()));

        FakeCommissionEarnedProvider provider = new FakeCommissionEarnedProvider(List.of(
                new Candidate(99L, new BigDecimal("500.00"), null, 1),   // 1st
                new Candidate(promoter.getId(), new BigDecimal("300.00"), null, 1), // 2nd — our promoter
                new Candidate(5L, new BigDecimal("100.00"), null, 1)));  // 3rd

        PromoterDashboardService svc = new PromoterDashboardService(promoterRepository, memberRepository,
                commissionRepository, competitiveRuleRepository, List.of(provider));

        PromoterDashboardDto dto = svc.getMyDashboard(userUuid);

        assertThat(dto.leaderboardPosition()).isEqualTo(2);
    }

    @Test
    void leaderboardPosition_isNull_whenPromoterHasNoActivityInTheWindow() {
        UUID userUuid = UUID.randomUUID();
        Promoter promoter = promoter();
        when(promoterRepository.findActiveByUserUuid(userUuid)).thenReturn(Optional.of(promoter));
        when(memberRepository.findPromoterPortfolio(promoter.getId())).thenReturn(List.of());
        when(commissionRepository.sumForPromoterInPeriod(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(competitiveRuleRepository.findByActiveTrue()).thenReturn(List.of(rankingRule()));

        FakeCommissionEarnedProvider provider = new FakeCommissionEarnedProvider(List.of(
                new Candidate(99L, new BigDecimal("500.00"), null, 1))); // someone else only

        PromoterDashboardService svc = new PromoterDashboardService(promoterRepository, memberRepository,
                commissionRepository, competitiveRuleRepository, List.of(provider));

        assertThat(svc.getMyDashboard(userUuid).leaderboardPosition()).isNull();
    }

    private static CompetitiveCommissionRule rankingRule() {
        CompetitiveCommissionRule rule = new CompetitiveCommissionRule();
        rule.setId(1L);
        rule.setUuid(UUID.randomUUID());
        rule.setCompetitionType(CompetitionType.RANKING);
        rule.setMetric(CompetitiveMetric.COMMISSION_EARNED);
        rule.setAccrualPeriodStrategy(PeriodAxisStrategy.MONTHLY);
        rule.setAchievementDateBasis(AchievementDateBasis.APPROVED_AT);
        rule.setIncludeSystemPromoters(false);
        return rule;
    }

    /** Minimal stand-in — only {@link #metric()} and {@link #snapshot} are ever called here. */
    private static final class FakeCommissionEarnedProvider implements CompetitiveMetricProvider {
        private final List<Candidate> candidates;

        FakeCommissionEarnedProvider(List<Candidate> candidates) {
            this.candidates = candidates;
        }

        @Override
        public CompetitiveMetric metric() {
            return CompetitiveMetric.COMMISSION_EARNED;
        }

        @Override
        public boolean supportsEvents() {
            return true;
        }

        @Override
        public List<com.fenixcore.optibienestar360.modules.promoter.metric.MetricEvent> events(
                PeriodStrategies.Window window, AchievementDateBasis basis, MetricScope scope) {
            return List.of();
        }

        @Override
        public List<Candidate> snapshot(PeriodStrategies.Window window, AchievementDateBasis basis, MetricScope scope) {
            return candidates;
        }
    }

    private Promoter promoter() {
        Promoter p = new Promoter();
        p.setId(7L);
        p.setUuid(UUID.randomUUID());
        p.setDisplayName("Ana Ventas");
        p.setReferralCode("ANAV42");
        p.setActive(true);
        return p;
    }

    private PromoterMemberRow row(String status) {
        return new PromoterMemberRow(UUID.randomUUID(), "Afiliado", status,
                status == null ? null : LocalDate.of(2026, 8, 1),
                status == null ? null : new BigDecimal("5.00"));
    }
}

package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionAward;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionManualDecision;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.AchievementDateBasis;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.CompetitionType;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.CompetitiveMetric;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.PeriodAxisStrategy;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.TiePolicy;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRulePosition;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRulePosition.RewardType;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.metric.CompetitiveMetricProvider;
import com.fenixcore.optibienestar360.modules.promoter.metric.CompetitiveRankingEngine.Candidate;
import com.fenixcore.optibienestar360.modules.promoter.repository.CompetitiveCommissionAwardRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CompetitiveCommissionManualDecisionRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CompetitiveCommissionRuleRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CompetitiveCommissionTieRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompetitiveCommissionEvaluationServiceTest {

    @Mock private CompetitiveCommissionRuleRepository ruleRepository;
    @Mock private CompetitiveCommissionAwardRepository awardRepository;
    @Mock private CompetitiveCommissionTieRepository tieRepository;
    @Mock private CompetitiveCommissionManualDecisionRepository manualDecisionRepository;
    @Mock private PromoterRepository promoterRepository;
    @Mock private CompetitiveMetricProvider provider;

    private static final Currency CURRENCY = currency();

    private CompetitiveCommissionEvaluationService sut(CompetitiveCommissionRule rule) {
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        lenient().when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        lenient().when(query.setParameter(anyString(), any())).thenReturn(query);
        lenient().when(query.getSingleResult()).thenReturn(0);
        lenient().when(provider.metric()).thenReturn(CompetitiveMetric.SALES_AMOUNT);
        lenient().when(promoterRepository.getReferenceById(any())).thenAnswer(inv -> promoter(inv.getArgument(0)));
        lenient().when(ruleRepository.findByUuid(rule.getUuid())).thenReturn(Optional.of(rule));

        CompetitiveCommissionEvaluationService service = new CompetitiveCommissionEvaluationService(
                ruleRepository, awardRepository, tieRepository, manualDecisionRepository, promoterRepository,
                List.of(provider), entityManager, new ObjectMapper());
        service.indexProviders();
        return service;
    }

    private static Currency currency() {
        Currency currency = new Currency();
        currency.setId(1L);
        currency.setCode("USD");
        return currency;
    }

    private static Promoter promoter(Long id) {
        Promoter promoter = new Promoter();
        promoter.setId(id);
        promoter.setUuid(UUID.randomUUID());
        return promoter;
    }

    private static CompetitiveCommissionRulePosition position(int from, int to) {
        CompetitiveCommissionRulePosition position = new CompetitiveCommissionRulePosition();
        position.setId(1L);
        position.setPositionFrom(from);
        position.setPositionTo(to);
        position.setRewardType(RewardType.FLAT);
        position.setFlatAmount(new BigDecimal("100.00"));
        position.setRewardCurrency(CURRENCY);
        return position;
    }

    private static CompetitiveCommissionRule rankingRule(CompetitiveCommissionRulePosition... positions) {
        CompetitiveCommissionRule rule = new CompetitiveCommissionRule();
        rule.setId(10L);
        rule.setUuid(UUID.randomUUID());
        rule.setName("Top ventas");
        rule.setMetric(CompetitiveMetric.SALES_AMOUNT);
        rule.setCompetitionType(CompetitionType.RANKING);
        rule.setAchievementDateBasis(AchievementDateBasis.APPROVED_AT);
        rule.setTiePolicy(TiePolicy.MANUAL);
        rule.setAccrualPeriodStrategy(PeriodAxisStrategy.MONTHLY);
        rule.setPartialSettlementPeriodStrategy(PeriodAxisStrategy.MONTHLY);
        rule.setFinalSettlementPeriodStrategy(PeriodAxisStrategy.MONTHLY);
        rule.setRetroactiveSettlementPeriodStrategy(PeriodAxisStrategy.MONTHLY);
        rule.setConfirmationDelayDays((short) 2);
        rule.getPositions().addAll(List.of(positions));
        for (CompetitiveCommissionRulePosition p : positions) {
            p.setRule(rule);
        }
        return rule;
    }

    @Test
    void evaluateRule_createsProvisionalAwardsForWinners() {
        when(awardRepository.findByRule_IdAndPeriodStartAndActiveTrueAndStatusNot(any(), any(), any()))
                .thenReturn(List.of());
        when(provider.snapshot(any(), any(), any())).thenReturn(List.of(
                new Candidate(1L, new BigDecimal("500.00"), Instant.parse("2026-09-10T00:00:00Z"), 3)));

        CompetitiveCommissionRule rule = rankingRule(position(1, 1));
        var outcome = sut(rule).evaluateRule(rule.getUuid(), LocalDate.of(2026, 9, 15), false);

        assertThat(outcome.created()).isEqualTo(1);
        assertThat(outcome.displaced()).isZero();
    }

    @Test
    void evaluateRule_dryRun_neverSaves() {
        when(awardRepository.findByRule_IdAndPeriodStartAndActiveTrueAndStatusNot(any(), any(), any()))
                .thenReturn(List.of());
        when(provider.snapshot(any(), any(), any())).thenReturn(List.of(
                new Candidate(1L, new BigDecimal("500.00"), Instant.parse("2026-09-10T00:00:00Z"), 3)));

        CompetitiveCommissionRule rule = rankingRule(position(1, 1));
        sut(rule).evaluateRule(rule.getUuid(), LocalDate.of(2026, 9, 15), true);

        org.mockito.Mockito.verify(awardRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void evaluateRule_displacesProvisionalLoserAndSparesConfirmedWinner() {
        CompetitiveCommissionRule rule = rankingRule(position(1, 1));

        CompetitiveCommissionAward pendingAward = new CompetitiveCommissionAward();
        pendingAward.setId(100L);
        pendingAward.setPromoter(promoter(9L));
        pendingAward.setAwardPosition(1);
        pendingAward.setStatus("PENDING");

        CompetitiveCommissionAward provisionalAward = new CompetitiveCommissionAward();
        provisionalAward.setId(101L);
        provisionalAward.setPromoter(promoter(2L));
        provisionalAward.setAwardPosition(1);
        provisionalAward.setStatus("PROVISIONAL");

        when(awardRepository.findByRule_IdAndPeriodStartAndActiveTrueAndStatusNot(any(), any(), any()))
                .thenReturn(List.of(pendingAward, provisionalAward));
        // Promoter 2 (previously a PROVISIONAL winner) now ranks below promoter 9's pinned position 1.
        when(provider.snapshot(any(), any(), any())).thenReturn(List.of(
                new Candidate(9L, new BigDecimal("999.00"), Instant.parse("2026-09-10T00:00:00Z"), 1),
                new Candidate(2L, new BigDecimal("300.00"), Instant.parse("2026-09-11T00:00:00Z"), 1)));

        sut(rule).evaluateRule(rule.getUuid(), LocalDate.of(2026, 9, 15), false);

        assertThat(pendingAward.getStatus()).isEqualTo("PENDING"); // untouched — pinned
        assertThat(provisionalAward.getStatus()).isEqualTo("VOIDED");
        assertThat(provisionalAward.getVoidReason()).isEqualTo("DISPLACED");
    }

    @Test
    void evaluateRule_activeDisqualifyDecision_excludesThatPromoter() {
        CompetitiveCommissionRule rule = rankingRule(position(1, 1));
        CompetitiveCommissionManualDecision disqualify = new CompetitiveCommissionManualDecision();
        disqualify.setKind(CompetitiveCommissionManualDecision.Kind.DISQUALIFY);
        disqualify.setPromoter(promoter(1L));
        when(awardRepository.findByRule_IdAndPeriodStartAndActiveTrueAndStatusNot(any(), any(), any()))
                .thenReturn(List.of());
        when(manualDecisionRepository.findByRule_IdAndPeriodStartAndActiveTrueAndStatus(any(), any(), any()))
                .thenReturn(List.of(disqualify));
        when(provider.snapshot(any(), any(), any())).thenReturn(List.of(
                new Candidate(1L, new BigDecimal("999.00"), Instant.parse("2026-09-10T00:00:00Z"), 1),
                new Candidate(2L, new BigDecimal("500.00"), Instant.parse("2026-09-11T00:00:00Z"), 1)));

        var outcome = sut(rule).evaluateRule(rule.getUuid(), LocalDate.of(2026, 9, 15), false);

        assertThat(outcome.created()).isEqualTo(1); // only promoter 2 wins — 1 is excluded
    }

    @Test
    void evaluateRule_redirectDecision_pinsReplacementAtGivenPosition() {
        CompetitiveCommissionRule rule = rankingRule(position(1, 1));
        CompetitiveCommissionManualDecision redirect = new CompetitiveCommissionManualDecision();
        redirect.setKind(CompetitiveCommissionManualDecision.Kind.REDIRECT);
        redirect.setAwardPosition(1);
        redirect.setPromoter(promoter(2L));
        redirect.setReplacedPromoter(promoter(1L));
        when(awardRepository.findByRule_IdAndPeriodStartAndActiveTrueAndStatusNot(any(), any(), any()))
                .thenReturn(List.of());
        when(manualDecisionRepository.findByRule_IdAndPeriodStartAndActiveTrueAndStatus(any(), any(), any()))
                .thenReturn(List.of(redirect));
        when(provider.snapshot(any(), any(), any())).thenReturn(List.of(
                new Candidate(1L, new BigDecimal("999.00"), Instant.parse("2026-09-10T00:00:00Z"), 1),
                new Candidate(2L, new BigDecimal("500.00"), Instant.parse("2026-09-11T00:00:00Z"), 1)));

        var outcome = sut(rule).evaluateRule(rule.getUuid(), LocalDate.of(2026, 9, 15), false);

        assertThat(outcome.created()).isEqualTo(1);
        verify(awardRepository).save(argThat(a -> a.getPromoter().getId() == 2L && a.getAwardPosition() == 1));
    }

    @Test
    void confirmDuePeriods_freezesProvisionalPastItsDelay() {
        CompetitiveCommissionRule rule = rankingRule(position(1, 1));

        CompetitiveCommissionAward due = new CompetitiveCommissionAward();
        due.setStatus("PROVISIONAL");
        due.setPeriodEnd(LocalDate.of(2026, 9, 1)); // + 2 days delay = due 2026-09-03

        CompetitiveCommissionAward notYetDue = new CompetitiveCommissionAward();
        notYetDue.setStatus("PROVISIONAL");
        notYetDue.setPeriodEnd(LocalDate.of(2026, 9, 14)); // due 2026-09-16

        when(awardRepository.findByRule_IdAndActiveTrueAndStatus(any(), org.mockito.ArgumentMatchers.eq("PROVISIONAL")))
                .thenReturn(List.of(due, notYetDue));

        int confirmed = sut(rule).confirmDuePeriods(rule.getUuid(), LocalDate.of(2026, 9, 15));

        assertThat(confirmed).isEqualTo(1);
        assertThat(due.getStatus()).isEqualTo("PENDING");
        assertThat(notYetDue.getStatus()).isEqualTo("PROVISIONAL");
    }
}

package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionAward;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionAwardSettlement;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionAwardSettlement.CutKind;
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
import com.fenixcore.optibienestar360.modules.promoter.repository.CompetitiveCommissionAwardSettlementRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CompetitiveCommissionRuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompetitiveCommissionSettlementServiceTest {

    @Mock private CompetitiveCommissionRuleRepository ruleRepository;
    @Mock private CompetitiveCommissionAwardRepository awardRepository;
    @Mock private CompetitiveCommissionAwardSettlementRepository settlementRepository;
    @Mock private CompetitiveMetricProvider provider;

    private CompetitiveCommissionSettlementService sut() {
        lenient().when(provider.metric()).thenReturn(CompetitiveMetric.SALES_AMOUNT);
        CompetitiveCommissionSettlementService service = new CompetitiveCommissionSettlementService(
                ruleRepository, awardRepository, settlementRepository, List.of(provider));
        service.indexProviders();
        return service;
    }

    private static Currency currency() {
        Currency currency = new Currency();
        currency.setId(1L);
        currency.setCode("USD");
        return currency;
    }

    private static Promoter promoter(long id) {
        Promoter promoter = new Promoter();
        promoter.setId(id);
        promoter.setUuid(UUID.randomUUID());
        return promoter;
    }

    private static CompetitiveCommissionRulePosition flatPosition() {
        CompetitiveCommissionRulePosition position = new CompetitiveCommissionRulePosition();
        position.setPositionFrom(1);
        position.setPositionTo(1);
        position.setRewardType(RewardType.FLAT);
        position.setFlatAmount(new BigDecimal("100.00"));
        position.setRewardCurrency(currency());
        return position;
    }

    private static CompetitiveCommissionRulePosition percentagePosition() {
        CompetitiveCommissionRulePosition position = new CompetitiveCommissionRulePosition();
        position.setPositionFrom(1);
        position.setPositionTo(1);
        position.setRewardType(RewardType.PERCENTAGE);
        position.setRewardPct(new BigDecimal("10.00"));
        position.setRewardCurrency(currency());
        return position;
    }

    private static CompetitiveCommissionRule rule(CompetitionType type, PeriodAxisStrategy partial,
                                                   PeriodAxisStrategy retroactive) {
        CompetitiveCommissionRule rule = new CompetitiveCommissionRule();
        rule.setId(1L);
        rule.setUuid(UUID.randomUUID());
        rule.setMetric(CompetitiveMetric.SALES_AMOUNT);
        rule.setCompetitionType(type);
        rule.setAchievementDateBasis(AchievementDateBasis.APPROVED_AT);
        rule.setTiePolicy(TiePolicy.MANUAL);
        rule.setAccrualPeriodStrategy(PeriodAxisStrategy.MONTHLY);
        rule.setPartialSettlementPeriodStrategy(partial);
        rule.setFinalSettlementPeriodStrategy(PeriodAxisStrategy.MONTHLY);
        rule.setRetroactiveSettlementPeriodStrategy(retroactive);
        return rule;
    }

    private static CompetitiveCommissionAward award(Promoter promoter, CompetitiveCommissionRulePosition position,
                                                      BigDecimal amount, BigDecimal basisAmount) {
        CompetitiveCommissionAward award = new CompetitiveCommissionAward();
        award.setId(50L);
        award.setPromoter(promoter);
        award.setPosition(position);
        award.setAwardPosition(1);
        award.setRewardType(position.getRewardType());
        award.setFlatAmount(position.getFlatAmount());
        award.setRewardPct(position.getRewardPct());
        award.setAmount(amount);
        award.setBasisAmount(basisAmount);
        award.setCurrency(position.getRewardCurrency());
        award.setStatus("PENDING");
        return award;
    }

    @Test
    void executeCutForRule_ranking_singleFinalCutEqualsAwardAmount() {
        CompetitiveCommissionRule rule = rule(CompetitionType.RANKING, PeriodAxisStrategy.MONTHLY, PeriodAxisStrategy.MONTHLY);
        when(ruleRepository.findByUuid(rule.getUuid())).thenReturn(Optional.of(rule));
        Promoter promoter = promoter(1L);
        CompetitiveCommissionAward award = award(promoter, flatPosition(), new BigDecimal("100.00"), null);
        when(awardRepository.findByRule_IdAndPeriodStartAndActiveTrueAndStatusNot(any(), any(), any()))
                .thenReturn(List.of(award));
        when(settlementRepository.findByRule_IdAndPromoter_IdAndPeriodStartAndCutKindAndCutSequence(
                any(), any(), any(), eq(CutKind.FINAL), eq(1))).thenReturn(Optional.empty());
        when(settlementRepository.sumPaidForRulePromoterPeriod(any(), any(), any())).thenReturn(BigDecimal.ZERO);

        var outcome = sut().executeCutForRule(rule.getUuid(), LocalDate.of(2026, 9, 30), false);

        assertThat(outcome.settlementsCreated()).isEqualTo(1);
        ArgumentCaptor<CompetitiveCommissionAwardSettlement> captor = ArgumentCaptor.forClass(CompetitiveCommissionAwardSettlement.class);
        verify(settlementRepository).save(captor.capture());
        assertThat(captor.getValue().getCutKind()).isEqualTo(CutKind.FINAL);
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void executeCutForRule_notAtAnyCutBoundary_doesNothing() {
        CompetitiveCommissionRule rule = rule(CompetitionType.RANKING, PeriodAxisStrategy.MONTHLY, PeriodAxisStrategy.MONTHLY);
        when(ruleRepository.findByUuid(rule.getUuid())).thenReturn(Optional.of(rule));

        var outcome = sut().executeCutForRule(rule.getUuid(), LocalDate.of(2026, 9, 15), false);

        assertThat(outcome.settlementsCreated()).isZero();
        assertThat(outcome.settlementsSkippedZero()).isZero();
    }

    @Test
    void executeCutForRule_percentageMidPeriodCut_recomputesBasisFromProvider() {
        // WEEKLY partial inside a MONTHLY accrual, no separate retroactive axis.
        CompetitiveCommissionRule rule = rule(CompetitionType.FIRST_TO_REACH, PeriodAxisStrategy.WEEKLY, PeriodAxisStrategy.WEEKLY);
        when(ruleRepository.findByUuid(rule.getUuid())).thenReturn(Optional.of(rule));
        Promoter promoter = promoter(1L);
        CompetitiveCommissionAward award = award(promoter, percentagePosition(), new BigDecimal("100.00"), new BigDecimal("1000.00"));
        when(awardRepository.findByRule_IdAndPeriodStartAndActiveTrueAndStatusNot(any(), any(), any()))
                .thenReturn(List.of(award));
        when(settlementRepository.findByRule_IdAndPromoter_IdAndPeriodStartAndCutKindAndCutSequence(
                any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(Optional.empty());
        when(settlementRepository.sumPaidForRulePromoterPeriod(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        // 2026-09-01 is a Tuesday; the ISO week 2026-08-31..09-06 clipped to the month starts 09-01.
        when(provider.snapshot(any(), any(), any())).thenReturn(
                List.of(new Candidate(1L, new BigDecimal("400.00"), null, 2)));

        var outcome = sut().executeCutForRule(rule.getUuid(), LocalDate.of(2026, 9, 6), false);

        assertThat(outcome.settlementsCreated()).isEqualTo(1);
        ArgumentCaptor<CompetitiveCommissionAwardSettlement> captor = ArgumentCaptor.forClass(CompetitiveCommissionAwardSettlement.class);
        verify(settlementRepository, times(1)).save(captor.capture());
        // basis 400 * 10% = 40.00 — nowhere near the award's eventual full-period 100.00.
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("40.00");
        assertThat(captor.getValue().getCutKind()).isEqualTo(CutKind.PARTIAL);
    }

    @Test
    void executeCutForRule_alreadyPaidCut_isNeverRecomputed() {
        CompetitiveCommissionRule rule = rule(CompetitionType.RANKING, PeriodAxisStrategy.MONTHLY, PeriodAxisStrategy.MONTHLY);
        when(ruleRepository.findByUuid(rule.getUuid())).thenReturn(Optional.of(rule));
        Promoter promoter = promoter(1L);
        CompetitiveCommissionAward award = award(promoter, flatPosition(), new BigDecimal("100.00"), null);
        when(awardRepository.findByRule_IdAndPeriodStartAndActiveTrueAndStatusNot(any(), any(), any()))
                .thenReturn(List.of(award));
        CompetitiveCommissionAwardSettlement paid = new CompetitiveCommissionAwardSettlement();
        paid.setStatus("PAID");
        when(settlementRepository.findByRule_IdAndPromoter_IdAndPeriodStartAndCutKindAndCutSequence(
                any(), any(), any(), eq(CutKind.FINAL), eq(1))).thenReturn(Optional.of(paid));

        var outcome = sut().executeCutForRule(rule.getUuid(), LocalDate.of(2026, 9, 30), false);

        assertThat(outcome.settlementsCreated()).isZero();
        verify(settlementRepository, times(0)).save(any());
    }
}

package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.service.CurrencyConversionService;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterMetricCount;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.AccrualMode;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.BonusMetric;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.RewardType;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.WindowStrategy;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterBonusAward;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterBonusAward.AwardStatus;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterBonusAward.CutKind;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionBonusRuleRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterBonusAwardRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fase B (hub plan competitive-commission-rules §12) — the settlement-cut
 * engine that connects a bonus rule's partial/final/retroactive axes (V146)
 * to real awards. Covers the plan's own test list: PER_BLOCK with a partial
 * (monthly, inside a quarterly accrual) cut, THRESHOLD paid once, PERCENTAGE
 * with a netted retroactive top-up + idempotency, plus the LIFETIME
 * regression (same numbers as the pre-Fase-B engine, just cut-tagged).
 */
@ExtendWith(MockitoExtension.class)
class BonusSettlementCutServiceTest {

    @Mock private CommissionBonusRuleRepository ruleRepository;
    @Mock private PromoterBonusAwardRepository awardRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private PromoterRepository promoterRepository;
    @Mock private CommissionRepository commissionRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private CurrencyConversionService currencyConversionService;

    private BonusSettlementCutService service() {
        return new BonusSettlementCutService(ruleRepository, awardRepository,
                memberRepository, promoterRepository, commissionRepository,
                paymentRepository, currencyConversionService);
    }

    private static final LocalDate LIFETIME_START = LocalDate.of(1970, 1, 1);

    // ─── LIFETIME / PER_BLOCK / FLAT — regression + reuse + new-seq-after-paid ──

    @Test
    void lifetime_grantsFirstBlock_asFinalCutSeq1() {
        CommissionBonusRule rule = lifetimeRule(500, new BigDecimal("100.00"));
        LocalDate asOf = LocalDate.of(2026, 6, 15);
        stubRuleLookup(rule);
        when(memberRepository.countNewSubscribersByPromoter(LIFETIME_START, asOf, false))
                .thenReturn(List.of(new PromoterMetricCount(7L, 523L)));
        Promoter promoter = promoter(7L);
        when(promoterRepository.findById(7L)).thenReturn(Optional.of(promoter));
        when(awardRepository.findTopByRule_IdAndPromoter_IdAndWindowStartAndCutKindOrderByCutSequenceDesc(
                rule.getId(), 7L, LIFETIME_START, CutKind.FINAL)).thenReturn(Optional.empty());
        when(awardRepository.sumGrantedExcludingCut(rule.getId(), 7L, LIFETIME_START, CutKind.FINAL, (short) 1))
                .thenReturn(BigDecimal.ZERO);
        when(awardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var outcome = service().executeCutForRule(rule.getUuid(), asOf, false);

        PromoterBonusAward saved = captureSaved();
        assertThat(saved.getCutKind()).isEqualTo(CutKind.FINAL);
        assertThat(saved.getCutSequence()).isEqualTo((short) 1);
        assertThat(saved.getCutStart()).isEqualTo(LIFETIME_START);
        assertThat(saved.getCutEnd()).isEqualTo(asOf);
        assertThat(saved.getWindowStart()).isEqualTo(LIFETIME_START);
        assertThat(saved.getWindowEnd()).isEqualTo(asOf);
        assertThat(saved.getAmount()).isEqualByComparingTo("100.00");
        assertThat(saved.getBlocksAwarded()).isEqualTo(1);
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(outcome.granted()).isEqualTo(1);
    }

    @Test
    void lifetime_reusesOpenRow_whenMoreBlocksCompletedSinceLastRun() {
        CommissionBonusRule rule = lifetimeRule(500, new BigDecimal("100.00"));
        LocalDate asOf = LocalDate.of(2026, 6, 16);
        stubRuleLookup(rule);
        when(memberRepository.countNewSubscribersByPromoter(LIFETIME_START, asOf, false))
                .thenReturn(List.of(new PromoterMetricCount(7L, 1040L))); // now 2 blocks worth
        when(promoterRepository.findById(7L)).thenReturn(Optional.of(promoter(7L)));

        PromoterBonusAward existing = new PromoterBonusAward();
        existing.setCutKind(CutKind.FINAL);
        existing.setCutSequence((short) 1);
        existing.setStatus(AwardStatus.PENDING.name());
        existing.setAmount(new BigDecimal("100.00"));
        when(awardRepository.findTopByRule_IdAndPromoter_IdAndWindowStartAndCutKindOrderByCutSequenceDesc(
                rule.getId(), 7L, LIFETIME_START, CutKind.FINAL)).thenReturn(Optional.of(existing));
        // Excludes the very row being recomputed — nothing else exists, so 0.
        when(awardRepository.sumGrantedExcludingCut(rule.getId(), 7L, LIFETIME_START, CutKind.FINAL, (short) 1))
                .thenReturn(BigDecimal.ZERO);
        when(awardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().executeCutForRule(rule.getUuid(), asOf, false);

        PromoterBonusAward saved = captureSaved();
        assertThat(saved.getCutSequence()).isEqualTo((short) 1); // same row, not a new one
        assertThat(saved.getAmount()).isEqualByComparingTo("200.00"); // full cumulative total now
        assertThat(saved.getBlocksAwarded()).isEqualTo(2);
    }

    @Test
    void lifetime_startsNewSequence_oncePreviousRowIsPaid() {
        CommissionBonusRule rule = lifetimeRule(500, new BigDecimal("100.00"));
        LocalDate asOf = LocalDate.of(2026, 7, 1);
        stubRuleLookup(rule);
        when(memberRepository.countNewSubscribersByPromoter(LIFETIME_START, asOf, false))
                .thenReturn(List.of(new PromoterMetricCount(7L, 1560L))); // 3 blocks worth now
        when(promoterRepository.findById(7L)).thenReturn(Optional.of(promoter(7L)));

        PromoterBonusAward paid = new PromoterBonusAward();
        paid.setCutKind(CutKind.FINAL);
        paid.setCutSequence((short) 1);
        paid.setStatus(AwardStatus.PAID.name());
        paid.setAmount(new BigDecimal("200.00"));
        when(awardRepository.findTopByRule_IdAndPromoter_IdAndWindowStartAndCutKindOrderByCutSequenceDesc(
                rule.getId(), 7L, LIFETIME_START, CutKind.FINAL)).thenReturn(Optional.of(paid));
        when(awardRepository.sumGrantedExcludingCut(rule.getId(), 7L, LIFETIME_START, CutKind.FINAL, (short) 2))
                .thenReturn(new BigDecimal("200.00")); // the paid row still counts toward netting
        when(awardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().executeCutForRule(rule.getUuid(), asOf, false);

        PromoterBonusAward saved = captureSaved();
        assertThat(saved.getCutSequence()).isEqualTo((short) 2); // fresh row, previous stays paid/untouched
        assertThat(saved.getAmount()).isEqualByComparingTo("100.00"); // 300 total - 200 already paid
    }

    @Test
    void lifetime_belowThreshold_grantsNothing() {
        CommissionBonusRule rule = lifetimeRule(500, new BigDecimal("100.00"));
        LocalDate asOf = LocalDate.of(2026, 6, 15);
        stubRuleLookup(rule);
        when(memberRepository.countNewSubscribersByPromoter(LIFETIME_START, asOf, false))
                .thenReturn(List.of(new PromoterMetricCount(7L, 200L)));
        when(promoterRepository.findById(7L)).thenReturn(Optional.of(promoter(7L)));

        var outcome = service().executeCutForRule(rule.getUuid(), asOf, false);

        verify(awardRepository, never()).save(any());
        assertThat(outcome.granted()).isZero();
    }

    // ─── THRESHOLD / MONTHLY / FLAT — paid once, never reopened ─────────────────

    @Test
    void thresholdMonthly_grantsOnFinalCut_whenThresholdReached() {
        CommissionBonusRule rule = monthlyThresholdRule(300, new BigDecimal("50.00"));
        LocalDate windowStart = LocalDate.of(2026, 6, 1);
        LocalDate windowEnd = LocalDate.of(2026, 6, 30);
        stubRuleLookup(rule);
        when(memberRepository.countActiveSubscribersByPromoter(false))
                .thenReturn(List.of(new PromoterMetricCount(7L, 312L)));
        when(promoterRepository.findById(7L)).thenReturn(Optional.of(promoter(7L)));
        when(awardRepository.findByRule_IdAndPromoter_IdAndWindowStartAndCutKindAndCutSequence(
                rule.getId(), 7L, windowStart, CutKind.FINAL, (short) 1)).thenReturn(Optional.empty());
        when(awardRepository.sumGrantedExcludingCut(rule.getId(), 7L, windowStart, CutKind.FINAL, (short) 1))
                .thenReturn(BigDecimal.ZERO);
        when(awardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var outcome = service().executeCutForRule(rule.getUuid(), windowEnd, false);

        PromoterBonusAward saved = captureSaved();
        assertThat(saved.getCutKind()).isEqualTo(CutKind.FINAL);
        assertThat(saved.getCutSequence()).isEqualTo((short) 1);
        assertThat(saved.getWindowStart()).isEqualTo(windowStart);
        assertThat(saved.getWindowEnd()).isEqualTo(windowEnd);
        assertThat(saved.getAmount()).isEqualByComparingTo("50.00");
        assertThat(outcome.granted()).isEqualTo(1);
    }

    @Test
    void thresholdMonthly_neverReopens_onceTheFinalCutIsPaid() {
        CommissionBonusRule rule = monthlyThresholdRule(300, new BigDecimal("50.00"));
        LocalDate windowStart = LocalDate.of(2026, 6, 1);
        LocalDate windowEnd = LocalDate.of(2026, 6, 30);
        stubRuleLookup(rule);
        when(memberRepository.countActiveSubscribersByPromoter(false))
                .thenReturn(List.of(new PromoterMetricCount(7L, 312L)));
        when(promoterRepository.findById(7L)).thenReturn(Optional.of(promoter(7L)));

        PromoterBonusAward paid = new PromoterBonusAward();
        paid.setStatus(AwardStatus.PAID.name());
        when(awardRepository.findByRule_IdAndPromoter_IdAndWindowStartAndCutKindAndCutSequence(
                rule.getId(), 7L, windowStart, CutKind.FINAL, (short) 1)).thenReturn(Optional.of(paid));

        var outcome = service().executeCutForRule(rule.getUuid(), windowEnd, false);

        verify(awardRepository, never()).save(any());
        assertThat(outcome.granted()).isZero();
    }

    // ─── PER_BLOCK partial cut (quarterly accrual, monthly partial cadence) ─────

    @Test
    void perBlock_partialCut_paysOnlyNewBlocksSinceThePreviousPartialCut() {
        CommissionBonusRule rule = new CommissionBonusRule();
        rule.setId(300L);
        rule.setUuid(UUID.randomUUID());
        rule.setActive(true);
        rule.setName("cada 100 nuevos, corte mensual");
        rule.setRewardCurrency(usd());
        rule.setMetric(BonusMetric.NEW_SUBSCRIBERS);
        rule.setAccrual(AccrualMode.PER_BLOCK);
        rule.setThresholdCount(100);
        rule.setRewardType(RewardType.FLAT);
        rule.setFlatAmount(new BigDecimal("10.00"));
        rule.setAccrualPeriodStrategy(WindowStrategy.QUARTERLY);
        rule.setPartialSettlementPeriodStrategy(WindowStrategy.MONTHLY);
        rule.setFinalSettlementPeriodStrategy(WindowStrategy.QUARTERLY);
        rule.setRetroactiveSettlementPeriodStrategy(WindowStrategy.QUARTERLY); // == final, no extra axis
        stubRuleLookup(rule);
        when(promoterRepository.findById(7L)).thenReturn(Optional.of(promoter(7L)));

        LocalDate quarterStart = LocalDate.of(2026, 4, 1);
        LocalDate aprEnd = LocalDate.of(2026, 4, 30);
        LocalDate mayEnd = LocalDate.of(2026, 5, 31);

        // Month 1 (April): 250 new → 2 blocks → $20.
        when(memberRepository.countNewSubscribersByPromoter(quarterStart, aprEnd, false))
                .thenReturn(List.of(new PromoterMetricCount(7L, 250L)));
        when(awardRepository.findByRule_IdAndPromoter_IdAndWindowStartAndCutKindAndCutSequence(
                rule.getId(), 7L, quarterStart, CutKind.PARTIAL, (short) 1)).thenReturn(Optional.empty());
        when(awardRepository.sumGrantedExcludingCut(rule.getId(), 7L, quarterStart, CutKind.PARTIAL, (short) 1))
                .thenReturn(BigDecimal.ZERO);
        when(awardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().executeCutForRule(rule.getUuid(), aprEnd, false);

        PromoterBonusAward april = captureSaved();
        assertThat(april.getCutKind()).isEqualTo(CutKind.PARTIAL);
        assertThat(april.getCutSequence()).isEqualTo((short) 1);
        assertThat(april.getCutStart()).isEqualTo(quarterStart);
        assertThat(april.getCutEnd()).isEqualTo(aprEnd);
        assertThat(april.getAmount()).isEqualByComparingTo("20.00");
        assertThat(april.getBlocksAwarded()).isEqualTo(2);

        // Month 2 (May, cumulative from quarter start): 340 new → 3 blocks → $30,
        // nets against April's $20 → only $10 (the one new block) is granted.
        when(memberRepository.countNewSubscribersByPromoter(quarterStart, mayEnd, false))
                .thenReturn(List.of(new PromoterMetricCount(7L, 340L)));
        when(awardRepository.findByRule_IdAndPromoter_IdAndWindowStartAndCutKindAndCutSequence(
                rule.getId(), 7L, quarterStart, CutKind.PARTIAL, (short) 2)).thenReturn(Optional.empty());
        when(awardRepository.sumGrantedExcludingCut(rule.getId(), 7L, quarterStart, CutKind.PARTIAL, (short) 2))
                .thenReturn(new BigDecimal("20.00"));

        service().executeCutForRule(rule.getUuid(), mayEnd, false);

        ArgumentCaptor<PromoterBonusAward> captor = ArgumentCaptor.forClass(PromoterBonusAward.class);
        verify(awardRepository, times(2)).save(captor.capture());
        PromoterBonusAward may = captor.getAllValues().get(1);
        assertThat(may.getCutKind()).isEqualTo(CutKind.PARTIAL);
        assertThat(may.getCutSequence()).isEqualTo((short) 2);
        assertThat(may.getCutStart()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(may.getCutEnd()).isEqualTo(mayEnd);
        assertThat(may.getAmount()).isEqualByComparingTo("10.00");
        assertThat(may.getBlocksAwarded()).isEqualTo(1);
    }

    // ─── PERCENTAGE retroactive top-up — netted + idempotent ────────────────────

    @Test
    void percentage_retroactiveCut_netsAgainstAlreadyGranted_andGrowsOverTime() {
        CommissionBonusRule rule = new CommissionBonusRule();
        rule.setId(400L);
        rule.setUuid(UUID.randomUUID());
        rule.setActive(true);
        rule.setName("10% de comision sobre 10 nuevos");
        rule.setRewardCurrency(usd());
        rule.setMetric(BonusMetric.NEW_SUBSCRIBERS);
        rule.setAccrual(AccrualMode.THRESHOLD);
        rule.setThresholdCount(10);
        rule.setRewardType(RewardType.PERCENTAGE);
        rule.setRewardPct(new BigDecimal("10.00"));
        rule.setAccrualPeriodStrategy(WindowStrategy.MONTHLY);
        rule.setPartialSettlementPeriodStrategy(WindowStrategy.MONTHLY); // == accrual, no partial cuts
        rule.setFinalSettlementPeriodStrategy(WindowStrategy.MONTHLY);
        rule.setRetroactiveSettlementPeriodStrategy(WindowStrategy.WEEKLY); // != partial → fires mid-month
        stubRuleLookup(rule);
        when(promoterRepository.findById(7L)).thenReturn(Optional.of(promoter(7L)));

        LocalDate monthStart = LocalDate.of(2026, 6, 1); // a Monday
        LocalDate week1End = LocalDate.of(2026, 6, 7);
        LocalDate week2End = LocalDate.of(2026, 6, 14);

        when(memberRepository.countNewSubscribersByPromoter(monthStart, week1End, false))
                .thenReturn(List.of(new PromoterMetricCount(7L, 15L)));
        when(commissionRepository.sumForPromoterInPeriod(7L, monthStart, week1End))
                .thenReturn(new BigDecimal("1000.00"));
        when(awardRepository.findByRule_IdAndPromoter_IdAndWindowStartAndCutKindAndCutSequence(
                rule.getId(), 7L, monthStart, CutKind.RETROACTIVE, (short) 1)).thenReturn(Optional.empty());
        when(awardRepository.sumGrantedExcludingCut(rule.getId(), 7L, monthStart, CutKind.RETROACTIVE, (short) 1))
                .thenReturn(BigDecimal.ZERO);
        when(awardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().executeCutForRule(rule.getUuid(), week1End, false);

        PromoterBonusAward week1 = captureSaved();
        assertThat(week1.getCutKind()).isEqualTo(CutKind.RETROACTIVE);
        assertThat(week1.getCutSequence()).isEqualTo((short) 1);
        assertThat(week1.getBasisAmount()).isEqualByComparingTo("1000.00");
        assertThat(week1.getAmount()).isEqualByComparingTo("100.00");

        // Idempotency: re-running the SAME cut (same basis) nets to the same
        // amount on the SAME row — no duplicate, no double-count.
        when(awardRepository.findByRule_IdAndPromoter_IdAndWindowStartAndCutKindAndCutSequence(
                rule.getId(), 7L, monthStart, CutKind.RETROACTIVE, (short) 1)).thenReturn(Optional.of(week1));

        service().executeCutForRule(rule.getUuid(), week1End, false);

        ArgumentCaptor<PromoterBonusAward> reRun = ArgumentCaptor.forClass(PromoterBonusAward.class);
        verify(awardRepository, times(2)).save(reRun.capture());
        assertThat(reRun.getAllValues().get(1).getCutSequence()).isEqualTo((short) 1);
        assertThat(reRun.getAllValues().get(1).getAmount()).isEqualByComparingTo("100.00");

        // Week 2: basis grew to 2500 → entitlement 250, nets against the 100
        // already granted → only the 150 delta is a new RETROACTIVE/2 row.
        when(memberRepository.countNewSubscribersByPromoter(monthStart, week2End, false))
                .thenReturn(List.of(new PromoterMetricCount(7L, 20L)));
        when(commissionRepository.sumForPromoterInPeriod(7L, monthStart, week2End))
                .thenReturn(new BigDecimal("2500.00"));
        when(awardRepository.findByRule_IdAndPromoter_IdAndWindowStartAndCutKindAndCutSequence(
                rule.getId(), 7L, monthStart, CutKind.RETROACTIVE, (short) 2)).thenReturn(Optional.empty());
        when(awardRepository.sumGrantedExcludingCut(rule.getId(), 7L, monthStart, CutKind.RETROACTIVE, (short) 2))
                .thenReturn(new BigDecimal("100.00"));

        service().executeCutForRule(rule.getUuid(), week2End, false);

        ArgumentCaptor<PromoterBonusAward> week2Captor = ArgumentCaptor.forClass(PromoterBonusAward.class);
        verify(awardRepository, times(3)).save(week2Captor.capture());
        PromoterBonusAward week2 = week2Captor.getAllValues().get(2);
        assertThat(week2.getCutKind()).isEqualTo(CutKind.RETROACTIVE);
        assertThat(week2.getCutSequence()).isEqualTo((short) 2);
        assertThat(week2.getBasisAmount()).isEqualByComparingTo("2500.00");
        assertThat(week2.getAmount()).isEqualByComparingTo("150.00");
    }

    @Test
    void percentage_flatReward_neverGrantsOnARetroactiveCut() {
        CommissionBonusRule rule = new CommissionBonusRule();
        rule.setId(500L);
        rule.setUuid(UUID.randomUUID());
        rule.setActive(true);
        rule.setName("umbral FLAT, sin retroactivo");
        rule.setRewardCurrency(usd());
        rule.setMetric(BonusMetric.NEW_SUBSCRIBERS);
        rule.setAccrual(AccrualMode.THRESHOLD);
        rule.setThresholdCount(10);
        rule.setRewardType(RewardType.FLAT);
        rule.setFlatAmount(new BigDecimal("50.00"));
        rule.setAccrualPeriodStrategy(WindowStrategy.MONTHLY);
        rule.setPartialSettlementPeriodStrategy(WindowStrategy.MONTHLY);
        rule.setFinalSettlementPeriodStrategy(WindowStrategy.MONTHLY);
        rule.setRetroactiveSettlementPeriodStrategy(WindowStrategy.WEEKLY);
        stubRuleLookup(rule);
        when(promoterRepository.findById(7L)).thenReturn(Optional.of(promoter(7L)));
        LocalDate monthStart = LocalDate.of(2026, 6, 1);
        LocalDate week1End = LocalDate.of(2026, 6, 7);
        when(memberRepository.countNewSubscribersByPromoter(monthStart, week1End, false))
                .thenReturn(List.of(new PromoterMetricCount(7L, 15L)));

        var outcome = service().executeCutForRule(rule.getUuid(), week1End, false);

        verify(awardRepository, never()).save(any());
        assertThat(outcome.granted()).isZero();
    }

    // ─── forceEvaluateRule (manual "Evaluate now" trigger) ──────────────────────

    @Test
    void forceEvaluateRule_reusesOpenLifetimeRow_justLikeTheScheduledPath() {
        CommissionBonusRule rule = lifetimeRule(500, new BigDecimal("100.00"));
        LocalDate asOf = LocalDate.of(2026, 6, 15);
        when(memberRepository.countNewSubscribersByPromoter(LIFETIME_START, asOf, false))
                .thenReturn(List.of(new PromoterMetricCount(7L, 523L)));
        when(promoterRepository.findById(7L)).thenReturn(Optional.of(promoter(7L)));
        when(awardRepository.findTopByRule_IdAndPromoter_IdAndWindowStartAndCutKindOrderByCutSequenceDesc(
                rule.getId(), 7L, LIFETIME_START, CutKind.FINAL)).thenReturn(Optional.empty());
        when(awardRepository.sumGrantedExcludingCut(rule.getId(), 7L, LIFETIME_START, CutKind.FINAL, (short) 1))
                .thenReturn(BigDecimal.ZERO);
        when(awardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var outcome = service().forceEvaluateRule(rule, asOf, false);

        assertThat(outcome.promotersAwarded()).isEqualTo(1);
        assertThat(outcome.totalBlocks()).isEqualTo(1);
        assertThat(outcome.amount()).isEqualByComparingTo("100.00");
    }

    @Test
    void forceEvaluateRule_dryRun_reportsWithoutPersisting() {
        CommissionBonusRule rule = lifetimeRule(500, new BigDecimal("100.00"));
        LocalDate asOf = LocalDate.of(2026, 6, 15);
        when(memberRepository.countNewSubscribersByPromoter(LIFETIME_START, asOf, false))
                .thenReturn(List.of(new PromoterMetricCount(7L, 523L)));
        when(promoterRepository.findById(7L)).thenReturn(Optional.of(promoter(7L)));
        when(awardRepository.findTopByRule_IdAndPromoter_IdAndWindowStartAndCutKindOrderByCutSequenceDesc(
                rule.getId(), 7L, LIFETIME_START, CutKind.FINAL)).thenReturn(Optional.empty());
        when(awardRepository.sumGrantedExcludingCut(rule.getId(), 7L, LIFETIME_START, CutKind.FINAL, (short) 1))
                .thenReturn(BigDecimal.ZERO);

        var outcome = service().forceEvaluateRule(rule, asOf, true);

        verify(awardRepository, never()).save(any());
        assertThat(outcome.amount()).isEqualByComparingTo("100.00");
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private void stubRuleLookup(CommissionBonusRule rule) {
        when(ruleRepository.findByUuid(rule.getUuid())).thenReturn(Optional.of(rule));
    }

    private PromoterBonusAward captureSaved() {
        ArgumentCaptor<PromoterBonusAward> captor = ArgumentCaptor.forClass(PromoterBonusAward.class);
        verify(awardRepository).save(captor.capture());
        return captor.getValue();
    }

    private CommissionBonusRule lifetimeRule(int threshold, BigDecimal flat) {
        CommissionBonusRule rule = baseRule("cada " + threshold + " nuevos");
        rule.setMetric(BonusMetric.NEW_SUBSCRIBERS);
        rule.setAccrual(AccrualMode.PER_BLOCK);
        rule.setAccrualPeriodStrategy(WindowStrategy.LIFETIME);
        rule.setPartialSettlementPeriodStrategy(WindowStrategy.MONTHLY);
        rule.setFinalSettlementPeriodStrategy(WindowStrategy.MONTHLY);
        rule.setRetroactiveSettlementPeriodStrategy(WindowStrategy.MONTHLY);
        rule.setThresholdCount(threshold);
        rule.setRewardType(RewardType.FLAT);
        rule.setFlatAmount(flat);
        return rule;
    }

    private CommissionBonusRule monthlyThresholdRule(int threshold, BigDecimal flat) {
        CommissionBonusRule rule = baseRule(threshold + " activos/mes");
        rule.setMetric(BonusMetric.ACTIVE_SUBSCRIBERS);
        rule.setAccrual(AccrualMode.THRESHOLD);
        rule.setAccrualPeriodStrategy(WindowStrategy.MONTHLY);
        rule.setPartialSettlementPeriodStrategy(WindowStrategy.MONTHLY);
        rule.setFinalSettlementPeriodStrategy(WindowStrategy.MONTHLY);
        rule.setRetroactiveSettlementPeriodStrategy(WindowStrategy.MONTHLY);
        rule.setThresholdCount(threshold);
        rule.setRewardType(RewardType.FLAT);
        rule.setFlatAmount(flat);
        return rule;
    }

    private CommissionBonusRule baseRule(String name) {
        CommissionBonusRule rule = new CommissionBonusRule();
        rule.setId(100L);
        rule.setUuid(UUID.randomUUID());
        rule.setActive(true);
        rule.setName(name);
        rule.setRewardCurrency(usd());
        return rule;
    }

    private static Currency usd() {
        Currency c = new Currency();
        c.setCode("USD");
        c.setName("Dolar estadounidense");
        c.setSymbol("US$");
        c.setDecimalPlaces((short) 2);
        return c;
    }

    private Promoter promoter(long id) {
        Promoter p = new Promoter();
        p.setId(id);
        p.setUuid(UUID.randomUUID());
        p.setReferralCode("P" + id);
        p.setActive(true);
        return p;
    }
}

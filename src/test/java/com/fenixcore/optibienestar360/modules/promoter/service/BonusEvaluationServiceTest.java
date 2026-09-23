package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.service.CurrencyConversionService;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.BonusEvaluationResponse;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterMetricCount;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.AccrualMode;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.BonusMetric;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.RewardType;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.WindowStrategy;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterBonusAward;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BonusEvaluationService} — the automated bonus engine
 * (v2 PDF #5). Locks the three shapes the business asked for (per-block over
 * lifetime, monthly threshold, campaign) plus the idempotency, dry-run and
 * multi-winner behaviour.
 */
@ExtendWith(MockitoExtension.class)
class BonusEvaluationServiceTest {

    @Mock private CommissionBonusRuleRepository ruleRepository;
    @Mock private PromoterBonusAwardRepository awardRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private PromoterRepository promoterRepository;
    @Mock private CommissionRepository commissionRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private CurrencyConversionService currencyConversionService;

    private BonusEvaluationService service() {
        return new BonusEvaluationService(ruleRepository, awardRepository,
                memberRepository, promoterRepository, commissionRepository,
                paymentRepository, currencyConversionService);
    }

    private static final LocalDate JUN_15 = LocalDate.of(2026, 6, 15);
    private static final LocalDate LIFETIME_START = LocalDate.of(1970, 1, 1);

    // ─── PER_BLOCK / NEW / LIFETIME / FLAT — "every 500 new → $100" ──────────

    @Test
    void perBlockLifetime_awardsOneBlock_forNewSubscribersOverThreshold() {
        CommissionBonusRule rule = newLifetimeRule(500, new BigDecimal("100.00"));
        stubRules(rule);
        Promoter p = promoter(7L);
        when(memberRepository.countNewSubscribersByPromoter(LIFETIME_START, JUN_15, false))
                .thenReturn(List.of(new PromoterMetricCount(7L, 523L)));
        when(promoterRepository.findAllById(any())).thenReturn(List.of(p));
        when(awardRepository.sumBlocksAwardedLifetime(rule.getId(), 7L)).thenReturn(0L);
        when(awardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BonusEvaluationResponse res = service().evaluate(JUN_15, false);

        PromoterBonusAward saved = captureSaved();
        assertThat(saved.getBlocksAwarded()).isEqualTo(1);
        assertThat(saved.getMetricCount()).isEqualTo(523);
        assertThat(saved.getAmount()).isEqualByComparingTo("100.00");
        assertThat(saved.getWindowStart()).isEqualTo(LIFETIME_START);
        assertThat(saved.getWindowEnd()).isEqualTo(JUN_15);
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getRuleNameSnapshot()).isEqualTo(rule.getName());
        assertThat(res.awardsCreated()).isEqualTo(1);
        assertThat(res.totalAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void perBlockLifetime_isIdempotent_whenBlockAlreadyAwarded() {
        CommissionBonusRule rule = newLifetimeRule(500, new BigDecimal("100.00"));
        stubRules(rule);
        when(memberRepository.countNewSubscribersByPromoter(LIFETIME_START, JUN_15, false))
                .thenReturn(List.of(new PromoterMetricCount(7L, 523L)));
        when(promoterRepository.findAllById(any())).thenReturn(List.of(promoter(7L)));
        when(awardRepository.sumBlocksAwardedLifetime(rule.getId(), 7L)).thenReturn(1L); // block already granted

        BonusEvaluationResponse res = service().evaluate(JUN_15, false);

        verify(awardRepository, never()).save(any());
        assertThat(res.awardsCreated()).isZero();
    }

    @Test
    void perBlock_belowThreshold_awardsNothing() {
        CommissionBonusRule rule = newLifetimeRule(500, new BigDecimal("100.00"));
        stubRules(rule);
        when(memberRepository.countNewSubscribersByPromoter(LIFETIME_START, JUN_15, false))
                .thenReturn(List.of(new PromoterMetricCount(7L, 200L))); // < 500
        when(promoterRepository.findAllById(any())).thenReturn(List.of(promoter(7L)));

        BonusEvaluationResponse res = service().evaluate(JUN_15, false);

        verify(awardRepository, never()).save(any());
        assertThat(res.awardsCreated()).isZero();
    }

    @Test
    void perBlockLifetime_multiplePromoters_eachWinTheirBlocks() {
        CommissionBonusRule rule = newLifetimeRule(500, new BigDecimal("100.00"));
        stubRules(rule);
        when(memberRepository.countNewSubscribersByPromoter(LIFETIME_START, JUN_15, false))
                .thenReturn(List.of(new PromoterMetricCount(7L, 523L), new PromoterMetricCount(8L, 1040L)));
        when(promoterRepository.findAllById(any())).thenReturn(List.of(promoter(7L), promoter(8L)));
        when(awardRepository.sumBlocksAwardedLifetime(eq(rule.getId()), any())).thenReturn(0L);
        when(awardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BonusEvaluationResponse res = service().evaluate(JUN_15, false);

        verify(awardRepository, org.mockito.Mockito.times(2)).save(any());
        assertThat(res.awardsCreated()).isEqualTo(2);
        // p7 → 1 block ($100), p8 → 2 blocks ($200) = $300, 3 blocks total.
        assertThat(res.totalAmount()).isEqualByComparingTo("300.00");
        assertThat(res.perRule().getFirst().blocksAwarded()).isEqualTo(3);
    }

    // ─── THRESHOLD / ACTIVE / MONTHLY / FLAT — "300 active/month → $50" ──────

    @Test
    void thresholdMonthly_awardsOnce_whenActiveCountReachesThreshold() {
        CommissionBonusRule rule = monthlyThresholdRule(300, new BigDecimal("50.00"));
        stubRules(rule);
        when(memberRepository.countActiveSubscribersByPromoter(false))
                .thenReturn(List.of(new PromoterMetricCount(7L, 312L)));
        when(promoterRepository.findAllById(any())).thenReturn(List.of(promoter(7L)));
        when(awardRepository.existsActiveInWindow(rule.getId(), 7L,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))).thenReturn(false);
        when(awardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BonusEvaluationResponse res = service().evaluate(JUN_15, false);

        PromoterBonusAward saved = captureSaved();
        assertThat(saved.getBlocksAwarded()).isEqualTo(1);
        assertThat(saved.getAmount()).isEqualByComparingTo("50.00");
        assertThat(saved.getWindowStart()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(saved.getWindowEnd()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(res.awardsCreated()).isEqualTo(1);
    }

    @Test
    void thresholdMonthly_isIdempotent_whenAlreadyAwardedInWindow() {
        CommissionBonusRule rule = monthlyThresholdRule(300, new BigDecimal("50.00"));
        stubRules(rule);
        when(memberRepository.countActiveSubscribersByPromoter(false))
                .thenReturn(List.of(new PromoterMetricCount(7L, 312L)));
        when(promoterRepository.findAllById(any())).thenReturn(List.of(promoter(7L)));
        when(awardRepository.existsActiveInWindow(rule.getId(), 7L,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))).thenReturn(true);

        BonusEvaluationResponse res = service().evaluate(JUN_15, false);

        verify(awardRepository, never()).save(any());
        assertThat(res.awardsCreated()).isZero();
    }

    // ─── dry-run ─────────────────────────────────────────────────────────────

    @Test
    void dryRun_reportsAwardsButPersistsNothing() {
        CommissionBonusRule rule = newLifetimeRule(500, new BigDecimal("100.00"));
        stubRules(rule);
        when(memberRepository.countNewSubscribersByPromoter(LIFETIME_START, JUN_15, false))
                .thenReturn(List.of(new PromoterMetricCount(7L, 523L)));
        when(promoterRepository.findAllById(any())).thenReturn(List.of(promoter(7L)));
        when(awardRepository.sumBlocksAwardedLifetime(rule.getId(), 7L)).thenReturn(0L);

        BonusEvaluationResponse res = service().evaluate(JUN_15, true);

        verify(awardRepository, never()).save(any());
        assertThat(res.dryRun()).isTrue();
        assertThat(res.awardsCreated()).isEqualTo(1);
        assertThat(res.totalAmount()).isEqualByComparingTo("100.00");
    }

    // ─── Promoter-type scoping (V46) ────────────────────────────────────────────

    @Test
    void typeScopedRule_skipsPromoterOfADifferentType() {
        com.fenixcore.optibienestar360.modules.catalog.entity.PromoterType wantedType = promoterType(9L);
        CommissionBonusRule rule = newLifetimeRule(500, new BigDecimal("100.00"));
        rule.setPromoterTypes(Set.of(wantedType));
        stubRules(rule);
        Promoter otherType = promoter(7L);
        otherType.setPromoterType(promoterType(1L));   // different type — rule doesn't apply
        when(memberRepository.countNewSubscribersByPromoter(LIFETIME_START, JUN_15, false))
                .thenReturn(List.of(new PromoterMetricCount(7L, 523L)));
        when(promoterRepository.findAllById(any())).thenReturn(List.of(otherType));

        BonusEvaluationResponse res = service().evaluate(JUN_15, false);

        verify(awardRepository, never()).save(any());
        assertThat(res.awardsCreated()).isZero();
    }

    @Test
    void typeScopedRule_appliesToPromoterOfTheMatchingType() {
        com.fenixcore.optibienestar360.modules.catalog.entity.PromoterType wantedType = promoterType(9L);
        CommissionBonusRule rule = newLifetimeRule(500, new BigDecimal("100.00"));
        rule.setPromoterTypes(Set.of(wantedType));
        stubRules(rule);
        Promoter matching = promoter(7L);
        matching.setPromoterType(promoterType(9L));
        when(memberRepository.countNewSubscribersByPromoter(LIFETIME_START, JUN_15, false))
                .thenReturn(List.of(new PromoterMetricCount(7L, 523L)));
        when(promoterRepository.findAllById(any())).thenReturn(List.of(matching));
        when(awardRepository.sumBlocksAwardedLifetime(rule.getId(), 7L)).thenReturn(0L);
        when(awardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BonusEvaluationResponse res = service().evaluate(JUN_15, false);

        assertThat(res.awardsCreated()).isEqualTo(1);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private void stubRules(CommissionBonusRule... rules) {
        when(ruleRepository.findByActiveTrue()).thenReturn(List.of(rules));
    }

    private PromoterBonusAward captureSaved() {
        ArgumentCaptor<PromoterBonusAward> captor = ArgumentCaptor.forClass(PromoterBonusAward.class);
        verify(awardRepository).save(captor.capture());
        return captor.getValue();
    }

    private CommissionBonusRule newLifetimeRule(int threshold, BigDecimal flat) {
        CommissionBonusRule rule = baseRule("cada " + threshold + " nuevos");
        rule.setMetric(BonusMetric.NEW_SUBSCRIBERS);
        rule.setAccrual(AccrualMode.PER_BLOCK);
        rule.setAccrualPeriodStrategy(WindowStrategy.LIFETIME);
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

    private com.fenixcore.optibienestar360.modules.catalog.entity.PromoterType promoterType(long id) {
        com.fenixcore.optibienestar360.modules.catalog.entity.PromoterType t =
                new com.fenixcore.optibienestar360.modules.catalog.entity.PromoterType();
        t.setId(id);
        t.setUuid(UUID.randomUUID());
        t.setCode("TYPE-" + id);
        t.setName("Type " + id);
        return t;
    }
}

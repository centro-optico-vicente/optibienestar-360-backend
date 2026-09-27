package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.promoter.dto.BonusEvaluationResponse;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.AccrualMode;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.BonusMetric;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionBonusRuleRepository;
import com.fenixcore.optibienestar360.modules.promoter.service.BonusSettlementCutService.ForcedOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BonusEvaluationService} is now a thin wrapper (Fase B) — the real
 * entitlement/netting math lives in {@link BonusSettlementCutServiceTest}.
 * This only locks the wrapper's contract: it calls {@link
 * BonusSettlementCutService#forceEvaluateRule} once per active rule and
 * aggregates the results into the unchanged {@link BonusEvaluationResponse}
 * shape.
 */
@ExtendWith(MockitoExtension.class)
class BonusEvaluationServiceTest {

    @Mock private CommissionBonusRuleRepository ruleRepository;
    @Mock private BonusSettlementCutService settlementCutService;

    private BonusEvaluationService service() {
        return new BonusEvaluationService(ruleRepository, settlementCutService);
    }

    private static final LocalDate JUN_15 = LocalDate.of(2026, 6, 15);

    private CommissionBonusRule rule() {
        CommissionBonusRule rule = new CommissionBonusRule();
        rule.setId(100L);
        rule.setUuid(UUID.randomUUID());
        rule.setActive(true);
        rule.setName("cada 500 nuevos");
        rule.setMetric(BonusMetric.NEW_SUBSCRIBERS);
        rule.setAccrual(AccrualMode.PER_BLOCK);
        return rule;
    }

    @Test
    void evaluate_delegatesPerRule_andAggregatesTotals() {
        CommissionBonusRule ruleA = rule();
        CommissionBonusRule ruleB = rule();
        ruleB.setId(200L);
        ruleB.setUuid(UUID.randomUUID());
        when(ruleRepository.findByActiveTrue()).thenReturn(List.of(ruleA, ruleB));
        when(settlementCutService.forceEvaluateRule(eq(ruleA), eq(JUN_15), eq(false)))
                .thenReturn(new ForcedOutcome(1, 1, new BigDecimal("100.00")));
        when(settlementCutService.forceEvaluateRule(eq(ruleB), eq(JUN_15), eq(false)))
                .thenReturn(new ForcedOutcome(2, 3, new BigDecimal("300.00")));

        BonusEvaluationResponse res = service().evaluate(JUN_15, false);

        verify(settlementCutService).forceEvaluateRule(ruleA, JUN_15, false);
        verify(settlementCutService).forceEvaluateRule(ruleB, JUN_15, false);
        assertThat(res.rulesEvaluated()).isEqualTo(2);
        assertThat(res.awardsCreated()).isEqualTo(3);
        assertThat(res.totalAmount()).isEqualByComparingTo("400.00");
        assertThat(res.perRule()).hasSize(2);
        assertThat(res.perRule().get(0).promotersAwarded()).isEqualTo(1);
        assertThat(res.perRule().get(1).promotersAwarded()).isEqualTo(2);
    }

    @Test
    void evaluate_defaultsAsOfToToday_whenNull() {
        when(ruleRepository.findByActiveTrue()).thenReturn(List.of());

        BonusEvaluationResponse res = service().evaluate(null, false);

        assertThat(res.asOf()).isEqualTo(LocalDate.now());
        assertThat(res.rulesEvaluated()).isZero();
        assertThat(res.awardsCreated()).isZero();
        assertThat(res.totalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void evaluate_passesThroughDryRun() {
        CommissionBonusRule rule = rule();
        when(ruleRepository.findByActiveTrue()).thenReturn(List.of(rule));
        when(settlementCutService.forceEvaluateRule(eq(rule), eq(JUN_15), eq(true)))
                .thenReturn(new ForcedOutcome(0, 0, BigDecimal.ZERO));

        BonusEvaluationResponse res = service().evaluate(JUN_15, true);

        verify(settlementCutService).forceEvaluateRule(rule, JUN_15, true);
        assertThat(res.dryRun()).isTrue();
    }

    @Test
    void appliesToPromoterType_appliesToEveryone_whenRuleHasNoScopedTypes() {
        assertThat(BonusEvaluationService.appliesToPromoterType(rule(), promoter())).isTrue();
    }

    private static com.fenixcore.optibienestar360.modules.promoter.entity.Promoter promoter() {
        var p = new com.fenixcore.optibienestar360.modules.promoter.entity.Promoter();
        p.setId(1L);
        p.setUuid(UUID.randomUUID());
        return p;
    }
}

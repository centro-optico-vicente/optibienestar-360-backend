package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.catalog.repository.PromoterTypeRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.BonusRuleDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.BonusRuleRequest;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.AccrualMode;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.BonusMetric;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.RewardType;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionBonusRule.WindowStrategy;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionBonusRuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BonusRulesService} — the cross-field validation of a
 * bonus-rule configuration (v2 PDF #5): reward flat-xor-pct, PER_BLOCK ⇒ FLAT,
 * and CAMPAIGN date coherence, plus the 404 on an unknown rule.
 */
@ExtendWith(MockitoExtension.class)
class BonusRulesServiceTest {

    @Mock private CommissionBonusRuleRepository repository;
    @Mock private PromoterTypeRepository promoterTypeRepository;

    private BonusRulesService service() {
        return new BonusRulesService(repository, promoterTypeRepository);
    }

    @Test
    void create_persistsValidFlatMonthlyRule() {
        when(repository.save(any())).thenAnswer(inv -> {
            CommissionBonusRule r = inv.getArgument(0);
            r.setUuid(UUID.randomUUID());
            return r;
        });

        BonusRuleDto dto = service().create(flatMonthly());

        CommissionBonusRule saved = capture();
        assertThat(saved.getMetric()).isEqualTo(BonusMetric.ACTIVE_SUBSCRIBERS);
        assertThat(saved.getAccrual()).isEqualTo(AccrualMode.THRESHOLD);
        assertThat(saved.getFlatAmount()).isEqualByComparingTo("50.00");
        assertThat(saved.getRewardPct()).isNull();
        assertThat(saved.getRewardCurrency()).isEqualTo("USD");
        assertThat(dto.thresholdCount()).isEqualTo(300);
    }

    @Test
    void create_nonCampaignRule_dropsCampaignDates() {
        when(repository.save(any())).thenAnswer(inv -> {
            CommissionBonusRule r = inv.getArgument(0);
            r.setUuid(UUID.randomUUID());
            return r;
        });

        // A MONTHLY rule that erroneously carries campaign dates — they must be nulled.
        BonusRuleRequest req = new BonusRuleRequest("stray dates", null, null,
                BonusMetric.ACTIVE_SUBSCRIBERS, AccrualMode.THRESHOLD, 300, WindowStrategy.MONTHLY,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                RewardType.FLAT, new BigDecimal("50.00"), null, "USD", null);

        service().create(req);

        CommissionBonusRule saved = capture();
        assertThat(saved.getCampaignStart()).isNull();
        assertThat(saved.getCampaignEnd()).isNull();
    }

    @Test
    void create_rejectsFlatRewardWithPercentAlsoSet() {
        BonusRuleRequest req = new BonusRuleRequest("bad", null, null,
                BonusMetric.NEW_SUBSCRIBERS, AccrualMode.THRESHOLD, 100, WindowStrategy.MONTHLY,
                null, null, RewardType.FLAT, new BigDecimal("10.00"), new BigDecimal("5.00"), "USD", null);

        assertThatThrownBy(() -> service().create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("bonus_rule.reward.flat_xor_pct");
        verify(repository, never()).save(any());
    }

    @Test
    void create_rejectsPercentageRewardOnPerBlockAccrual() {
        BonusRuleRequest req = new BonusRuleRequest("bad", null, null,
                BonusMetric.NEW_SUBSCRIBERS, AccrualMode.PER_BLOCK, 500, WindowStrategy.LIFETIME,
                null, null, RewardType.PERCENTAGE, null, new BigDecimal("5.00"), "USD", null);

        assertThatThrownBy(() -> service().create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("bonus_rule.per_block.flat_only");
        verify(repository, never()).save(any());
    }

    @Test
    void create_rejectsCampaignWithoutDates() {
        BonusRuleRequest req = new BonusRuleRequest("campaign", null, null,
                BonusMetric.NEW_SUBSCRIBERS, AccrualMode.PER_BLOCK, 50, WindowStrategy.CAMPAIGN,
                null, null, RewardType.FLAT, new BigDecimal("200.00"), null, "USD", null);

        assertThatThrownBy(() -> service().create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("bonus_rule.campaign.dates_required");
        verify(repository, never()).save(any());
    }

    @Test
    void create_rejectsCampaignWithEndBeforeStart() {
        BonusRuleRequest req = new BonusRuleRequest("campaign", null, null,
                BonusMetric.NEW_SUBSCRIBERS, AccrualMode.PER_BLOCK, 50, WindowStrategy.CAMPAIGN,
                LocalDate.of(2026, 6, 30), LocalDate.of(2026, 6, 1),
                RewardType.FLAT, new BigDecimal("200.00"), null, "USD", null);

        assertThatThrownBy(() -> service().create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("bonus_rule.campaign.dates_order");
        verify(repository, never()).save(any());
    }

    @Test
    void update_404_whenRuleUnknown() {
        UUID uuid = UUID.randomUUID();
        when(repository.findByUuid(uuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().update(uuid, flatMonthly()))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("bonus_rule.not_found");
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private CommissionBonusRule capture() {
        ArgumentCaptor<CommissionBonusRule> captor = ArgumentCaptor.forClass(CommissionBonusRule.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    /** "300 active subscribers/month → $50" — a valid THRESHOLD/FLAT/MONTHLY rule. */
    private BonusRuleRequest flatMonthly() {
        return new BonusRuleRequest("300 activos/mes", "bono de cobranza", null,
                BonusMetric.ACTIVE_SUBSCRIBERS, AccrualMode.THRESHOLD, 300, WindowStrategy.MONTHLY,
                null, null, RewardType.FLAT, new BigDecimal("50.00"), null, null, null);
    }
}

package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.campaign.repository.CampaignRepository;
import com.fenixcore.optibienestar360.modules.catalog.repository.PromoterTypeRepository;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.modules.promoter.dto.CompetitiveRuleCreateRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.CompetitiveRulePositionRequest;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.CompetitionType;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.CompetitiveMetric;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.PeriodAxisStrategy;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRulePosition.RewardType;
import com.fenixcore.optibienestar360.modules.promoter.repository.CompetitiveCommissionRuleRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRankRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompetitiveCommissionRulesServiceTest {

    @Mock private CompetitiveCommissionRuleRepository repository;
    @Mock private PromoterTypeRepository promoterTypeRepository;
    @Mock private PromoterRankRepository rankRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private DefaultSortResolver defaultSortResolver;

    private static final UUID CURRENCY_UUID = UUID.randomUUID();

    private CompetitiveCommissionRulesService sut() {
        Currency currency = new Currency();
        currency.setUuid(CURRENCY_UUID);
        currency.setCode("USD");
        lenient().when(currencyRepository.findByUuid(CURRENCY_UUID)).thenReturn(Optional.of(currency));
        lenient().when(repository.findByCompetitionGroupAndActiveTrue(any())).thenReturn(List.of());
        lenient().when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return new CompetitiveCommissionRulesService(
                repository, promoterTypeRepository, rankRepository, currencyRepository, campaignRepository, defaultSortResolver);
    }

    private static CompetitiveRulePositionRequest firstPlace() {
        return new CompetitiveRulePositionRequest(1, 1, "1er lugar", RewardType.FLAT,
                new BigDecimal("100.00"), null, CURRENCY_UUID, null, null, null, null);
    }

    /** "First one to reach 100 new subscribers wins $100" — a valid FIRST_TO_REACH/count/FLAT rule. */
    private static CompetitiveRuleCreateRequest firstToReachCount(List<CompetitiveRulePositionRequest> positions) {
        return new CompetitiveRuleCreateRequest(
                "Primero en 100", null, CompetitiveMetric.NEW_SUBSCRIBERS, CompetitionType.FIRST_TO_REACH,
                100, null, null,
                null, null, null, null,
                PeriodAxisStrategy.MONTHLY, PeriodAxisStrategy.MONTHLY, PeriodAxisStrategy.MONTHLY, PeriodAxisStrategy.MONTHLY,
                null, null, null, null,
                null, null, null, null,
                null, null, null,
                positions);
    }

    @Test
    void create_persists_validFirstToReach() {
        var dto = sut().create(firstToReachCount(List.of(firstPlace())));

        assertThat(dto.maxWinners()).isEqualTo(1);
        assertThat(dto.positions()).hasSize(1);
        assertThat(dto.retroactiveSettlementPeriodStrategy()).isEqualTo(PeriodAxisStrategy.MONTHLY);
    }

    @Test
    void create_rejects_whenNoPositions() {
        assertThatThrownBy(() -> sut().create(firstToReachCount(List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("competitive_rule.positions.required");
    }

    @Test
    void create_rejects_whenPositionsOverlap() {
        var overlapping = new CompetitiveRulePositionRequest(1, 3, null, RewardType.FLAT,
                new BigDecimal("50.00"), null, CURRENCY_UUID, null, null, null, null);
        var second = new CompetitiveRulePositionRequest(2, 5, null, RewardType.FLAT,
                new BigDecimal("10.00"), null, CURRENCY_UUID, null, null, null, null);

        assertThatThrownBy(() -> sut().create(firstToReachCount(List.of(overlapping, second))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("competitive_rule.positions.overlap");
    }

    @Test
    void create_rejects_metricSnapshotWithFirstToReach() {
        // ACTIVE_SUBSCRIBERS never emits events (D4) — no achieved-at moment for FIRST_TO_REACH.
        var req = new CompetitiveRuleCreateRequest(
                "bad", null, CompetitiveMetric.ACTIVE_SUBSCRIBERS, CompetitionType.FIRST_TO_REACH,
                50, null, null, null, null, null, null,
                PeriodAxisStrategy.MONTHLY, PeriodAxisStrategy.MONTHLY, PeriodAxisStrategy.MONTHLY, PeriodAxisStrategy.MONTHLY,
                null, null, null, null, null, null, null, null, null, null, null,
                List.of(firstPlace()));

        assertThatThrownBy(() -> sut().create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("competitive_rule.metric_snapshot_ranking_only");
    }

    @Test
    void create_rejects_percentageRewardWithCountMetric() {
        // NEW_SUBSCRIBERS is a count metric — there's no "amount" to take a percentage of.
        var pctPosition = new CompetitiveRulePositionRequest(1, 1, null, RewardType.PERCENTAGE,
                null, new BigDecimal("5.00"), CURRENCY_UUID, null, null, null, null);

        assertThatThrownBy(() -> sut().create(firstToReachCount(List.of(pctPosition))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("competitive_rule.positions.percentage_requires_amount_metric");
    }

    @Test
    void create_rejects_rankingWithPartialFinerThanAccrual() {
        // D14: RANKING only ever pays at the accrual close (or the rule's ends_at) —
        // a weekly partial payout against a monthly ranking would pay a not-yet-final position.
        var req = new CompetitiveRuleCreateRequest(
                "top 1 mensual", null, CompetitiveMetric.SALES_COUNT, CompetitionType.RANKING,
                null, null, null, null, null, null, null,
                PeriodAxisStrategy.MONTHLY, PeriodAxisStrategy.WEEKLY, PeriodAxisStrategy.MONTHLY, PeriodAxisStrategy.MONTHLY,
                null, null, null, null, null, null, null, null, null, null, null,
                List.of(firstPlace()));

        assertThatThrownBy(() -> sut().create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("competitive_rule.ranking_close_only");
    }

    @Test
    void create_persists_rankingWithMatchingPartialSettlement() {
        var req = new CompetitiveRuleCreateRequest(
                "top 1 mensual", null, CompetitiveMetric.SALES_COUNT, CompetitionType.RANKING,
                null, null, null, null, null, null, null,
                PeriodAxisStrategy.MONTHLY, PeriodAxisStrategy.MONTHLY, PeriodAxisStrategy.MONTHLY, PeriodAxisStrategy.MONTHLY,
                null, null, null, null, null, null, null, null, null, null, null,
                List.of(firstPlace()));

        var dto = sut().create(req);
        assertThat(dto.retroactiveSettlementPeriodStrategy()).isEqualTo(PeriodAxisStrategy.MONTHLY);
    }

    @Test
    void create_rejects_minThresholdOnFirstToReachPosition() {
        // D12: min-per-position only applies to RANKING.
        var withMin = new CompetitiveRulePositionRequest(1, 1, null, RewardType.FLAT,
                new BigDecimal("100.00"), null, CURRENCY_UUID, null, null, 10, null);

        assertThatThrownBy(() -> sut().create(firstToReachCount(List.of(withMin))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("competitive_rule.positions.min_threshold_ranking_only");
    }

    @Test
    void create_rejects_groupPriorityWithoutGroup() {
        var req = new CompetitiveRuleCreateRequest(
                "bad", null, CompetitiveMetric.NEW_SUBSCRIBERS, CompetitionType.FIRST_TO_REACH,
                100, null, null, null, null, null, (short) 1,
                PeriodAxisStrategy.MONTHLY, PeriodAxisStrategy.MONTHLY, PeriodAxisStrategy.MONTHLY, PeriodAxisStrategy.MONTHLY,
                null, null, null, null, null, null, null, null, null, null, null,
                List.of(firstPlace()));

        assertThatThrownBy(() -> sut().create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("competitive_rule.group_priority_required");
    }

    @Test
    void create_rejects_thresholdMismatchWithMetric() {
        // NEW_SUBSCRIBERS is count-based — thresholdAmount doesn't apply.
        var req = new CompetitiveRuleCreateRequest(
                "bad", null, CompetitiveMetric.NEW_SUBSCRIBERS, CompetitionType.FIRST_TO_REACH,
                null, new BigDecimal("100.00"), CURRENCY_UUID, null, null, null, null,
                PeriodAxisStrategy.MONTHLY, PeriodAxisStrategy.MONTHLY, PeriodAxisStrategy.MONTHLY, PeriodAxisStrategy.MONTHLY,
                null, null, null, null, null, null, null, null, null, null, null,
                List.of(firstPlace()));

        assertThatThrownBy(() -> sut().create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("competitive_rule.threshold.metric_mismatch");
    }
}

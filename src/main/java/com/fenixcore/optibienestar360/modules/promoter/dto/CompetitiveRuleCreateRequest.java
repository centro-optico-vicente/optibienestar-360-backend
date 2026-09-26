package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.AchievementDateBasis;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.CompetitionType;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.CompetitiveMetric;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.PeriodAxisStrategy;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.TiePolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Payload for {@code POST /v1/admin/competitive-commission-rules}. Exactly
 * one of {@code thresholdCount}/{@code thresholdAmount} is required, chosen
 * by {@code metric} (count-based vs. amount-based — service-validated, same
 * XOR style as {@code CommissionTierCreateRequest}). {@code positions} is
 * the rule's full set of position ranges — at least one is required (D11).
 */
public record CompetitiveRuleCreateRequest(
        @NotBlank String name,
        String description,
        @NotNull CompetitiveMetric metric,
        @NotNull CompetitionType competitionType,
        @Min(1) Integer thresholdCount,
        @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal thresholdAmount,
        UUID thresholdCurrencyUuid,
        AchievementDateBasis achievementDateBasis,
        TiePolicy tiePolicy,
        String competitionGroup,
        @Min(1) Short groupPriority,
        @NotNull PeriodAxisStrategy accrualPeriodStrategy,
        PeriodAxisStrategy partialSettlementPeriodStrategy,
        PeriodAxisStrategy finalSettlementPeriodStrategy,
        PeriodAxisStrategy retroactiveSettlementPeriodStrategy,
        Short accrualPeriodAnchor,
        Short partialSettlementPeriodAnchor,
        Short finalSettlementPeriodAnchor,
        Short retroactiveSettlementPeriodAnchor,
        @Min(0) @Max(60) Short confirmationDelayDays,
        UUID campaignUuid,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        Boolean includeSystemPromoters,
        List<UUID> promoterTypeUuids,
        List<UUID> rankUuids,
        @NotEmpty @Valid List<CompetitiveRulePositionRequest> positions
) {
}

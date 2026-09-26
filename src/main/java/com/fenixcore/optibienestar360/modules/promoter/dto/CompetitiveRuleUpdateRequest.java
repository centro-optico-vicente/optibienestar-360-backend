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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Payload for {@code PUT /v1/admin/competitive-commission-rules/{uuid}} —
 * PATCH semantics field-by-field (a {@code null} field leaves the current
 * value untouched), EXCEPT {@code positions}, {@code promoterTypeUuids} and
 * {@code rankUuids}, which are full-replace when present (same convention as
 * {@code CommissionTierUpdateRequest.promoterTypeUuids}).
 */
public record CompetitiveRuleUpdateRequest(
        String name,
        String description,
        CompetitiveMetric metric,
        CompetitionType competitionType,
        @Min(1) Integer thresholdCount,
        @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal thresholdAmount,
        UUID thresholdCurrencyUuid,
        AchievementDateBasis achievementDateBasis,
        TiePolicy tiePolicy,
        String competitionGroup,
        @Min(1) Short groupPriority,
        PeriodAxisStrategy accrualPeriodStrategy,
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
        Boolean active,
        List<UUID> promoterTypeUuids,
        List<UUID> rankUuids,
        @Valid List<CompetitiveRulePositionRequest> positions
) {
}

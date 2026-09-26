package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.AchievementDateBasis;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.CompetitionType;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.CompetitiveMetric;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.PeriodAxisStrategy;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.TiePolicy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Detail DTO for one competitive commission rule. {@code maxWinners} is
 * derived from {@code max(position.positionTo)} (D3) — never persisted, so
 * it can never drift from the positions that actually define it.
 * {@code hasFrozenAwards} is always {@code false} in Fase 1 (no awards exist
 * yet — the D14 "congelamiento" 409 lands in Fase 2 once awards do).
 */
public record CompetitiveRuleDto(
        UUID uuid,
        String name,
        String description,
        @Display(Display.Kind.ENUM) CompetitiveMetric metric,
        @Display(Display.Kind.ENUM) CompetitionType competitionType,
        Integer thresholdCount,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "thresholdCurrency_Code") BigDecimal thresholdAmount,
        @Display DisplayRef thresholdCurrency,
        String thresholdCurrency_Code,
        @Display(Display.Kind.ENUM) AchievementDateBasis achievementDateBasis,
        @Display(Display.Kind.ENUM) TiePolicy tiePolicy,
        String competitionGroup,
        Short groupPriority,
        @Display(Display.Kind.ENUM) PeriodAxisStrategy accrualPeriodStrategy,
        @Display(Display.Kind.ENUM) PeriodAxisStrategy partialSettlementPeriodStrategy,
        @Display(Display.Kind.ENUM) PeriodAxisStrategy finalSettlementPeriodStrategy,
        @Display(Display.Kind.ENUM) PeriodAxisStrategy retroactiveSettlementPeriodStrategy,
        Short accrualPeriodAnchor,
        Short partialSettlementPeriodAnchor,
        Short finalSettlementPeriodAnchor,
        Short retroactiveSettlementPeriodAnchor,
        short confirmationDelayDays,
        @Display DisplayRef campaign,
        @Display(Display.Kind.DATETIME) OffsetDateTime startsAt,
        @Display(Display.Kind.DATETIME) OffsetDateTime endsAt,
        @Display(Display.Kind.BOOLEAN) boolean includeSystemPromoters,
        List<DisplayRef> promoterTypes,
        List<DisplayRef> ranks,
        List<CompetitiveRulePositionDto> positions,
        /** {@code max(position.positionTo)} — derived (D3), not a stored column. */
        int maxWinners,
        /** Always {@code false} in Fase 1 — see class Javadoc. */
        @Display(Display.Kind.BOOLEAN) boolean hasFrozenAwards,
        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(value = Display.Kind.ENUM, enumScope = "competitive_commission_rule.status") String status,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        @Display(Display.Kind.DATETIME) Instant updatedAt
) {
    public static CompetitiveRuleDto from(CompetitiveCommissionRule r) {
        List<CompetitiveRulePositionDto> positions = r.getPositions().stream()
                .map(CompetitiveRulePositionDto::from).toList();
        int maxWinners = r.getPositions().stream().mapToInt(p -> p.getPositionTo()).max().orElse(0);
        return new CompetitiveRuleDto(
                r.getUuid(), r.getName(), r.getDescription(), r.getMetric(), r.getCompetitionType(),
                r.getThresholdCount(), r.getThresholdAmount(),
                DisplayRefs.ref(r.getThresholdCurrency()),
                r.getThresholdCurrency() != null ? r.getThresholdCurrency().getCode() : null,
                r.getAchievementDateBasis(), r.getTiePolicy(), r.getCompetitionGroup(), r.getGroupPriority(),
                r.getAccrualPeriodStrategy(), r.getPartialSettlementPeriodStrategy(),
                r.getFinalSettlementPeriodStrategy(), r.getRetroactiveSettlementPeriodStrategy(),
                r.getAccrualPeriodAnchor(), r.getPartialSettlementPeriodAnchor(),
                r.getFinalSettlementPeriodAnchor(), r.getRetroactiveSettlementPeriodAnchor(),
                r.getConfirmationDelayDays(),
                DisplayRefs.ref(r.getCampaign()), r.getStartsAt(), r.getEndsAt(),
                r.isIncludeSystemPromoters(),
                r.getPromoterTypes().stream().map(DisplayRefs::ref).toList(),
                r.getRanks().stream().map(DisplayRefs::ref).toList(),
                positions, maxWinners, false,
                r.isActive(), r.getStatus(), r.getCreatedAt(), r.getUpdatedAt());
    }
}

package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.CompetitionType;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.CompetitiveMetric;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.UUID;

/**
 * Compact projection for the "Reglas competitivas" table — avoids serializing
 * the full {@code positions}/{@code promoterTypes}/{@code ranks} collections
 * of {@link CompetitiveRuleDto} (list projection vs. detail convention);
 * {@code positionsSummary} is the one derived string the table actually
 * shows (e.g. "1°: $100 · 2°-5°: $10").
 */
public record CompetitiveRuleListItemDto(
        UUID uuid,
        String name,
        @Display(Display.Kind.ENUM) CompetitiveMetric metric,
        @Display(Display.Kind.ENUM) CompetitionType competitionType,
        Integer thresholdCount,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "thresholdCurrency_Code") BigDecimal thresholdAmount,
        String thresholdCurrency_Code,
        String positionsSummary,
        int maxWinners,
        @Display DisplayRef campaign,
        @Display(Display.Kind.DATETIME) OffsetDateTime startsAt,
        @Display(Display.Kind.DATETIME) OffsetDateTime endsAt,
        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(value = Display.Kind.ENUM, enumScope = "competitive_commission_rule.status") String status,
        @Display(Display.Kind.DATETIME) Instant createdAt
) {
    public static CompetitiveRuleListItemDto from(CompetitiveCommissionRule r) {
        var positions = r.getPositions().stream()
                .sorted(Comparator.comparingInt(p -> p.getPositionFrom())).toList();
        String summary = positions.stream().map(p -> {
            String range = p.getPositionFrom() == p.getPositionTo()
                    ? p.getPositionFrom() + "°"
                    : p.getPositionFrom() + "°-" + p.getPositionTo() + "°";
            String reward = p.getFlatAmount() != null
                    ? "$" + p.getFlatAmount().stripTrailingZeros().toPlainString()
                    : p.getRewardPct() + "%";
            return range + ": " + reward;
        }).reduce((a, b) -> a + " · " + b).orElse("");
        int maxWinners = positions.stream().mapToInt(p -> p.getPositionTo()).max().orElse(0);
        return new CompetitiveRuleListItemDto(
                r.getUuid(), r.getName(), r.getMetric(), r.getCompetitionType(),
                r.getThresholdCount(), r.getThresholdAmount(),
                r.getThresholdCurrency() != null ? r.getThresholdCurrency().getCode() : null,
                summary, maxWinners,
                DisplayRefs.ref(r.getCampaign()), r.getStartsAt(), r.getEndsAt(),
                r.isActive(), r.getStatus(), r.getCreatedAt());
    }
}

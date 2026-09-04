package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;
import com.fenixcore.optibienestar360.modules.promoter.entity.LeaderboardPrize;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Output DTO for a configured leaderboard prize. Scalars carry a localized {@code _Display} sibling (ADR 0014). */
public record LeaderboardPrizeDto(
        UUID uuid,
        int rank,
        @Display(Display.Kind.ENUM) PeriodStrategy periodStrategy,
        @Display(Display.Kind.MONEY) BigDecimal prizeAmount,
        String prizeCurrency,
        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        @Display(Display.Kind.DATETIME) Instant updatedAt
) {
    public static LeaderboardPrizeDto from(LeaderboardPrize p) {
        return new LeaderboardPrizeDto(p.getUuid(), p.getRank(), p.getPeriodStrategy(),
                p.getPrizeAmount(), p.getPrizeCurrency().getCode(), p.isActive(), p.getCreatedAt(), p.getUpdatedAt());
    }
}

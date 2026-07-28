package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;
import com.fenixcore.optibienestar360.modules.promoter.entity.LeaderboardPrize;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Output DTO for a configured leaderboard prize. */
public record LeaderboardPrizeDto(
        UUID uuid,
        int rank,
        PeriodStrategy periodStrategy,
        BigDecimal prizeAmount,
        String prizeCurrency,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static LeaderboardPrizeDto from(LeaderboardPrize p) {
        return new LeaderboardPrizeDto(p.getUuid(), p.getRank(), p.getPeriodStrategy(),
                p.getPrizeAmount(), p.getPrizeCurrency(), p.isActive(), p.getCreatedAt(), p.getUpdatedAt());
    }
}

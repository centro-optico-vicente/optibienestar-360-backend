package com.fenixcore.optibienestar360.modules.promoter.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One ranked promoter on the leaderboard. {@code prizeAmount} is populated only
 * when a prize is configured for the entry's rank + strategy.
 */
public record LeaderboardEntryDto(
        int rank,
        UUID promoterUuid,
        String displayName,
        String referralCode,
        BigDecimal totalCommission,
        long commissionCount,
        BigDecimal prizeAmount,
        String prizeCurrency
) {}

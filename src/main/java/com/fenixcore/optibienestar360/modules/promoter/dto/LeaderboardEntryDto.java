package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.core.display.Display;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One ranked promoter on the leaderboard. {@code prizeAmount} is populated only
 * when a prize is configured for the entry's rank + strategy. Money scalars
 * carry a localized {@code _Display} sibling (hub ADR 0014).
 */
public record LeaderboardEntryDto(
        int rank,
        UUID promoterUuid,
        String displayName,
        String referralCode,
        @Display(Display.Kind.MONEY) BigDecimal totalCommission,
        long commissionCount,
        @Display(Display.Kind.MONEY) BigDecimal prizeAmount,
        String prizeCurrency
) {}

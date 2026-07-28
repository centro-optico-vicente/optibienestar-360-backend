package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Create/update payload for a leaderboard prize. Used for both POST and PUT —
 * all fields required (a prize is small enough to always send whole).
 */
public record LeaderboardPrizeRequest(
        @NotNull @Min(1) Integer rank,
        @NotNull PeriodStrategy periodStrategy,
        @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal prizeAmount,
        @Size(min = 3, max = 3) String prizeCurrency
) {}

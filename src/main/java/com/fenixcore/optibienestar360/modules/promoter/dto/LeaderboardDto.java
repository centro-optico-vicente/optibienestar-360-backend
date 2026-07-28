package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;

import java.time.LocalDate;
import java.util.List;

/** The leaderboard for one resolved period. */
public record LeaderboardDto(
        PeriodStrategy periodStrategy,
        LocalDate periodStart,
        LocalDate periodEnd,
        List<LeaderboardEntryDto> entries
) {}

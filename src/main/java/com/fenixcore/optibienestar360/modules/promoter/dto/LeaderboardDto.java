package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;

import java.time.LocalDate;
import java.util.List;

/** The leaderboard for one resolved period. Scalars carry a localized {@code _Display} sibling (ADR 0014). */
public record LeaderboardDto(
        @Display(Display.Kind.ENUM) PeriodStrategy periodStrategy,
        @Display(Display.Kind.DATE) LocalDate periodStart,
        @Display(Display.Kind.DATE) LocalDate periodEnd,
        List<LeaderboardEntryDto> entries
) {}

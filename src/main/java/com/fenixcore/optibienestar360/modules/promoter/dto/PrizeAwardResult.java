package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Outcome of awarding leaderboard prizes for one closed period. */
public record PrizeAwardResult(
        PeriodStrategy periodStrategy,
        LocalDate periodStart,
        LocalDate periodEnd,
        boolean dryRun,
        int awardsCreated,
        BigDecimal totalAmount,
        String currency
) {}

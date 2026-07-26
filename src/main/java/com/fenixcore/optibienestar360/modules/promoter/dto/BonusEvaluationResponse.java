package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Outcome of an evaluation run — the envelope returned by the manual trigger and
 * the shape the scheduled runner summarizes into its job-run record.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BonusEvaluationResponse(
        LocalDate asOf,
        boolean dryRun,
        int rulesEvaluated,
        int awardsCreated,
        BigDecimal totalAmount,
        String currency,
        Instant executedAt,
        List<RuleOutcome> perRule
) {

    /** Per-rule roll-up of what the run granted. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RuleOutcome(
            UUID ruleUuid,
            String ruleName,
            String metric,
            String accrual,
            int promotersAwarded,
            int blocksAwarded,
            BigDecimal amount
    ) {}
}

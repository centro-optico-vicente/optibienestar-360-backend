package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fenixcore.optibienestar360.core.display.Display;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Outcome of an evaluation run — the envelope returned by the manual trigger and
 * the shape the scheduled runner summarizes into its job-run record.
 *
 * <p>Scalars carry a localized {@code _Display} sibling (ADR 0014).</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BonusEvaluationResponse(
        @Display(Display.Kind.DATE) LocalDate asOf,
        @Display(Display.Kind.BOOLEAN) boolean dryRun,
        int rulesEvaluated,
        int awardsCreated,
        @Display(Display.Kind.MONEY) BigDecimal totalAmount,
        String currency,
        @Display(Display.Kind.DATETIME) Instant executedAt,
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
            @Display(Display.Kind.MONEY) BigDecimal amount
    ) {}
}

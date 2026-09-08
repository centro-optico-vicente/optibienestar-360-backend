package com.fenixcore.optibienestar360.modules.promoter.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Payload for {@code POST /v1/admin/hierarchy-overrides/re-rate} (hub plan
 * ".ai/plans/2026-09-07-hierarchical-commissions-plan.md" §2, PR3). Same
 * shape/intent as {@code CommissionReRatingRequest}, applied to {@code
 * promoter_hierarchy_overrides} instead: closes a period by re-rating every
 * PENDING override to the highest team-volume band the beneficiary's
 * (Supervisor's/Coordinador's) team reached, and by resyncing any override
 * whose {@code basisAmount} went stale because its source commission/override
 * was itself re-rated after the cascade first ran.
 *
 * <p>{@code dryRun = true} computes the deltas without writing — lets the
 * admin verify totals before committing at month close.</p>
 */
public record HierarchyOverrideReRatingRequest(
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        Boolean dryRun
) {}

package com.fenixcore.optibienestar360.modules.promoter.dto;

import java.time.LocalDate;

/**
 * Payload for the manual evaluation trigger
 * ({@code POST /v1/admin/bonus-rules/evaluate}).
 *
 * @param asOf   the reference date the windows are computed against; null →
 *               today (America/Caracas). Lets an admin re-run a past period.
 * @param dryRun true → compute + report the awards that <em>would</em> be
 *               granted without persisting anything.
 */
public record BonusEvaluationRequest(
        LocalDate asOf,
        boolean dryRun
) {}

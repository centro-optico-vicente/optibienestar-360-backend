package com.fenixcore.optibienestar360.modules.promoter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Payload for {@code POST /v1/admin/commissions/payout}. Closes a period
 * by marking every PENDING commission inside the date range as PAID,
 * generates a CSV breakdown per promoter, and emails each promoter
 * the summary + CSV.
 *
 * <p>{@code payoutReference} is required — it's the bank batch id / Zelle
 * confirmation / transfer reference that ties this disbursement to the
 * out-of-band money movement. Goes into every commission row's
 * {@code payout_reference} column and also into the email so the
 * promoter can match it against their bank statement.</p>
 *
 * <p>{@code dryRun} = {@code true} runs the calculation and returns the
 * per-promoter summary without committing any DB writes or emails —
 * lets admin verify totals before pulling the trigger.</p>
 */
public record CommissionPayoutRequest(
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        @NotBlank @Size(max = 120) String payoutReference,
        Boolean dryRun
) {}

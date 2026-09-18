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
 *
 * <p>{@code paymentMethod} (V117, hub plan payments-unification) is the
 * {@code payment_methods.code} (V115) recorded on the real payout
 * {@code payment_lines} row {@code CommissionPayoutService} now creates —
 * e.g. {@code "BANK_TRANSFER"}, {@code "ZELLE"}. Optional; defaults to
 * {@code "OTHER"} when omitted, same placeholder semantics
 * {@code BeneficiaryInscriptionBiller} already uses for a charge whose real
 * method isn't known yet — the admin can edit the line afterward like any
 * other payment.</p>
 */
public record CommissionPayoutRequest(
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        @NotBlank @Size(max = 120) String payoutReference,
        Boolean dryRun,
        @Size(max = 40) String paymentMethod
) {}

package com.fenixcore.optibienestar360.modules.promoter.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Payload for {@code POST /v1/admin/commissions/payout/by-selection} — pays
 * an ad-hoc set of {@code APPROVED} commission rows picked by hand from the
 * approval table (row-level granularity, mirrors {@link
 * ApproveCommissionsRequest}), instead of every row inside a date range
 * ({@link CommissionPayoutRequest}). Every {@code commissionUuids} row must
 * currently be {@code APPROVED}; the whole request is rejected (400)
 * otherwise, same all-or-nothing policy as {@code CommissionApprovalService}.
 *
 * <p>{@code paymentMethodUuid}/{@code currencyUuid} are FK references (ADR
 * 0014 {@code _Uuid} convention) rather than the natural-key code {@link
 * CommissionPayoutRequest#paymentMethod()} takes — the selection flow's UI
 * already resolves them from a {@code CommonEntityReferenceSelect}, so there
 * is no free-text code to default from here.</p>
 *
 * <p>{@code dryRun} = {@code true} runs the calculation and returns the
 * per-promoter summary without committing any DB writes or emails — same
 * preview semantics as the period-based payout.</p>
 */
public record CommissionPayoutBySelectionRequest(
        @NotEmpty List<UUID> commissionUuids,
        @NotNull UUID paymentMethodUuid,
        @NotNull UUID currencyUuid,
        @Size(max = 120) String payoutReference,
        boolean dryRun
) {}

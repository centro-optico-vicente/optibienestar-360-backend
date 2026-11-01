package com.fenixcore.optibienestar360.modules.membership.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Payload for {@code POST /v1/admin/members/{memberUuid}/memberships}.
 *
 * <p>The pricing snapshot (inscription_fee / monthly_fee / grace_period_days)
 * is NOT in the request — it's copied from the parent {@code planUuid} at
 * the moment of enrollment, so the active membership becomes immune to
 * later edits of the plan. To enroll on different terms, the admin edits
 * the Plan first or chooses a different one.</p>
 *
 * <p>{@code enrolledAt} defaults to today server-side when null. The
 * {@code nextDueDate} is computed as {@code enrolledAt + 1 month}
 * (calendar arithmetic) regardless of what the client sends — this prevents
 * the admin from accidentally enrolling someone with an overdue first
 * payment, and the daily status job stays simple.</p>
 *
 * <p>Term (hub ADR 0034): {@code expiresAt} (must be after {@code enrolledAt}) and {@code reinscriptionRequired}
 * override what the plan defines; with neither, the plan's rule decides.</p>
 */
public record MembershipCreateRequest(
        @NotNull UUID planUuid,
        LocalDate enrolledAt,
        LocalDate expiresAt,
        /** Optional promotion applied in the same transaction (hub ADR 0018); invalid → the whole enrollment fails. */
        UUID promotionUuid,
        @Size(max = 20) String promotionCode,
        /**
         * Optional override of the plan's re-inscription flag (hub ADR 0034); null = inherit. {@code true} on a plan
         * without a term rule needs an explicit {@code expiresAt} and renews every 12 months.
         */
        Boolean reinscriptionRequired
) {}

package com.fenixcore.optibienestar360.modules.membership.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.modules.membership.entity.TermMode;

import java.time.LocalDate;

/**
 * Response of {@code GET /v1/admin/plans/{uuid}/membership-terms} (hub ADR 0034): how many live memberships of the
 * plan comply with the plan's current term rule. {@code suggestedExpiresAt} is the first expiry the plan's rule
 * yields from today (FIXED_DATE: next occurrence of the month/day; PERIOD: today + N months); {@code null} when the
 * plan does not require re-inscription.
 */
public record PlanTermSummaryDto(
        long total,
        long complies,
        long differs,
        boolean planRequiresReinscription,
        @Display(Display.Kind.ENUM) TermMode termMode,
        Integer termFixedMonth,
        Integer termFixedDay,
        Integer termPeriodMonths,
        @Display(Display.Kind.DATE) LocalDate suggestedExpiresAt
) {}

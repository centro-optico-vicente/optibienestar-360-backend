package com.fenixcore.optibienestar360.modules.membership.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.membership.entity.TermMode;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Row of {@code GET /v1/admin/plans/{uuid}/membership-terms/memberships}: a live membership with its current term
 * snapshot and whether it complies with the plan's rule (hub ADR 0034).
 */
public record MembershipTermRowDto(
        UUID uuid,
        UUID memberUuid,
        @Display DisplayRef member,
        String documentNumber,
        String contractNumber,
        @Display(value = Display.Kind.ENUM, enumScope = "membership.status") String status,
        @Display(Display.Kind.DATE) LocalDate enrolledAt,
        @Display(Display.Kind.DATE) LocalDate expiresAt,
        @Display(Display.Kind.BOOLEAN) boolean reinscriptionRequired,
        @Display(Display.Kind.ENUM) TermMode termMode,
        Integer termFixedMonth,
        Integer termFixedDay,
        Integer termPeriodMonths,
        @Display(Display.Kind.DATE) LocalDate termStartedAt,
        @Display(Display.Kind.BOOLEAN) boolean complies
) {}

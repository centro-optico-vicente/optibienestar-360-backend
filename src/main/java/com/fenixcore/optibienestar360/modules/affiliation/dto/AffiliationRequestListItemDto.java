package com.fenixcore.optibienestar360.modules.affiliation.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;

import java.time.Instant;
import java.util.UUID;

/**
 * Compact row of the admin requests list ({@code GET /v1/admin/affiliation-requests})
 * and of the advisor's own list ({@code GET /v1/advisors/me/affiliation-requests}).
 * The applicant's identity stays flat; plan and advisor follow the
 * {@code _Display} convention (hub ADR 0014).
 */
public record AffiliationRequestListItemDto(
        UUID uuid,
        String fullName,
        String documentType,
        String documentNumber,
        String email,
        String phone,
        @Display DisplayRef plan,
        @Display DisplayRef advisor,
        @Display(value = Display.Kind.ENUM, enumScope = "affiliation_request.status") String status,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        @Display(Display.Kind.DATETIME) Instant reviewedAt
) {}

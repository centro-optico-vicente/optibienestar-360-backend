package com.fenixcore.optibienestar360.modules.member.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;

import java.time.Instant;
import java.util.UUID;

/**
 * A row of {@code GET /v1/admin/members/advisor/candidates} (ADR LOC-0016). {@code plan} and
 * {@code promoter} serialize as {@code plan_Uuid}/{@code plan_Display} and
 * {@code promoter_Uuid}/{@code promoter_Display} (hub ADR 0014), {@code null} when the member has
 * no active membership / no advisor. {@code status} is the member's own status (ACTIVE, SUSPENDED, INACTIVE…).
 *
 * @param document the formatted document, e.g. {@code V-12345678}
 */
public record MemberCandidateDto(
        UUID uuid,
        String displayName,
        String document,
        @Display DisplayRef plan,
        @Display(value = Display.Kind.ENUM, enumScope = "member.status") String status,
        @Display DisplayRef promoter,
        @Display(Display.Kind.DATETIME) Instant createdAt
) {}

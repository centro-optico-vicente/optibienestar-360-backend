package com.fenixcore.optibienestar360.modules.member.dto;

import java.util.UUID;

/**
 * Selection of members for the portfolio-distribution screen (ADR LOC-0016). Sent as query params
 * on {@code GET /v1/admin/members/advisor/candidates} and as the {@code filter} object of the
 * distribute body ("all N results").
 *
 * @param source             {@code UNASSIGNED} or {@code ADVISOR} (required)
 * @param sourcePromoterUuid the advisor whose portfolio is listed; required when {@code ADVISOR}
 *                           (inactive and system advisors are accepted)
 * @param q                  free text over the member's name and document number
 * @param planUuid           plan of the member's active membership
 * @param status             the member's own status (ACTIVE, SUSPENDED, INACTIVE…), same enum as the members list
 */
public record MemberCandidateFilter(
        MemberCandidateSource source,
        UUID sourcePromoterUuid,
        String q,
        UUID planUuid,
        String status) {
}

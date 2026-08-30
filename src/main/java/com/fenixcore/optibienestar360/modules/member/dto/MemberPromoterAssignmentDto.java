package com.fenixcore.optibienestar360.modules.member.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fenixcore.optibienestar360.core.display.Display;

import java.time.Instant;
import java.util.UUID;

/**
 * Result of a member↔promoter reassignment — echoes the audit row written by
 * {@code POST /v1/admin/members/{uuid}/assign-promoter}. {@code fromPromoter*}
 * is null when the member had no promoter before (NON_NULL — the null pair is
 * omitted, and its {@code _Display} sibling with it). {@code assignedAt} gets
 * a localized {@code _Display} (hub ADR 0014).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MemberPromoterAssignmentDto(
        UUID uuid,
        UUID memberUuid,
        String memberName,
        UUID fromPromoterUuid,
        String fromPromoterName,
        UUID toPromoterUuid,
        String toPromoterName,
        UUID actorUserUuid,
        String reason,
        @Display(Display.Kind.DATETIME) Instant assignedAt
) {}

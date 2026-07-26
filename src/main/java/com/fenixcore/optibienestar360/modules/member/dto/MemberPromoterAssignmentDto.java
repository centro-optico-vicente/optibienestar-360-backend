package com.fenixcore.optibienestar360.modules.member.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Result of a member↔promoter reassignment — echoes the audit row written by
 * {@code POST /v1/admin/members/{uuid}/assign-promoter}. {@code fromPromoter*}
 * is null when the member had no promoter before.
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
        Instant assignedAt
) {}

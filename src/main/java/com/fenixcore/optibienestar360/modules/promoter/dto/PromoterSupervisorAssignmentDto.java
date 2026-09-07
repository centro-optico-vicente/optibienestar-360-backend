package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fenixcore.optibienestar360.core.display.Display;

import java.time.Instant;
import java.util.UUID;

/**
 * Result of a supervisor (re)assignment — echoes the audit row written by
 * {@code POST /v1/admin/promoters/{uuid}/assign-supervisor}. {@code
 * fromSupervisor*}/{@code toSupervisor*} are null when there was/is no
 * supervisor on that side (NON_NULL — the null pair is omitted, and its
 * {@code _Display} sibling with it).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PromoterSupervisorAssignmentDto(
        UUID uuid,
        UUID promoterUuid,
        String promoterName,
        UUID fromSupervisorUuid,
        String fromSupervisorName,
        UUID toSupervisorUuid,
        String toSupervisorName,
        UUID actorUserUuid,
        String reason,
        @Display(Display.Kind.DATETIME) Instant assignedAt
) {}

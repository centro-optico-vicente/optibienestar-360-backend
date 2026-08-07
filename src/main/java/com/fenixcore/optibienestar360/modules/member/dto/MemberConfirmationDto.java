package com.fenixcore.optibienestar360.modules.member.dto;

import java.time.Instant;
import java.util.UUID;

/** Response for {@code POST /v1/admin/members/{uuid}/confirm}. */
public record MemberConfirmationDto(
        UUID memberUuid,
        Instant confirmedAt
) {}

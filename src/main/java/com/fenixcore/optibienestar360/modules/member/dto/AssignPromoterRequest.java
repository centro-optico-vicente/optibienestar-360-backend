package com.fenixcore.optibienestar360.modules.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Payload for {@code POST /v1/admin/members/{uuid}/assign-promoter} (v2 PDF 2.a).
 * Reassigns the permanent member↔promoter link. {@code reason} is mandatory —
 * the link is a permanent attribution and every change is audited, so the
 * operator must state why.
 */
public record AssignPromoterRequest(
        @NotNull UUID promoterUuid,
        @NotBlank @Size(max = 2000) String reason
) {}

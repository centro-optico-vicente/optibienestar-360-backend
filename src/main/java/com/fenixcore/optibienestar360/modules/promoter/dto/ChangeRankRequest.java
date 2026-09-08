package com.fenixcore.optibienestar360.modules.promoter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Payload for {@code POST /v1/admin/promoters/{uuid}/change-rank} — ascends
 * or demotes a promoter's cargo. {@code newSupervisorUuid} is required unless
 * {@code newRankUuid} is the top rank (no active rank above it); the client
 * is expected to have already queried {@code GET
 * /v1/admin/promoters/eligible-supervisors?rankUuid={newRankUuid}} to build
 * that field's options before submitting — so the two-step "pick the rank,
 * then pick from who's eligible at that rank" flow is enforced by the UI,
 * with this endpoint re-validating server-side regardless.
 */
public record ChangeRankRequest(
        @NotNull UUID newRankUuid,
        UUID newSupervisorUuid,
        @NotBlank @Size(max = 2000) String reason
) {}

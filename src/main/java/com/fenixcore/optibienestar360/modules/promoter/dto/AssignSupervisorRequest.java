package com.fenixcore.optibienestar360.modules.promoter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Payload for {@code POST /v1/admin/promoters/{uuid}/assign-supervisor}.
 * {@code supervisorUuid = null} explicitly clears the supervisor (promoter
 * becomes top of its own chain) — distinct from omitting the field, which
 * Jakarta Validation would otherwise reject as blank; {@code reason} is
 * always mandatory since every change is audited.
 */
public record AssignSupervisorRequest(
        UUID supervisorUuid,
        @NotBlank @Size(max = 2000) String reason
) {}

package com.fenixcore.optibienestar360.modules.ally.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Body of {@code POST /v1/admin/allies/advisor/bulk-assign} (ADR LOC-0016): the allies to move and
 * the advisor they get (active, non-system — checked by the service, 422 otherwise).
 */
public record AllyBulkAssignAdvisorRequest(
        @NotEmpty @Size(max = 500) List<@NotNull UUID> allyUuids,
        @NotNull UUID promoterUuid
) {}

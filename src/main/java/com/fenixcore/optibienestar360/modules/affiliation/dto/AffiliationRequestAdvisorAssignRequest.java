package com.fenixcore.optibienestar360.modules.affiliation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Body of {@code PUT /v1/admin/affiliation-requests/{uuid}/advisor} (ADR LOC-0016): the new advisor
 * (active, non-system) and an optional reason kept in the request's log.
 */
public record AffiliationRequestAdvisorAssignRequest(
        @NotNull UUID promoterUuid,
        @Size(max = 500) String reason
) {}

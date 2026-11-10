package com.fenixcore.optibienestar360.modules.ally.dto;

import java.util.UUID;

/**
 * Body of {@code PUT /v1/admin/allies/{uuid}/advisor}: the advisor (promoter) who brought the ally
 * into OptiBienestar 360. {@code null} clears it.
 */
public record AllyAdvisorAssignRequest(UUID promoterUuid) {}

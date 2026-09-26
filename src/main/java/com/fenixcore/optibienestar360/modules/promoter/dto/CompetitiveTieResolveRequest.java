package com.fenixcore.optibienestar360.modules.promoter.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Request body for {@code POST /v1/admin/competitive-commission-winners/ties/{uuid}/resolve}. */
public record CompetitiveTieResolveRequest(
        @NotEmpty List<UUID> winnerPromoterUuids,
        @Size(min = 10) String reason
) {}

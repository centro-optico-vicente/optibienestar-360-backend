package com.fenixcore.optibienestar360.modules.promoter.dto;

import jakarta.validation.constraints.Size;

/** Request body for {@code POST /v1/admin/competitive-commission-winners/decisions/{uuid}/revert}. */
public record CompetitiveDecisionRevertRequest(@Size(min = 10) String reason) {
}

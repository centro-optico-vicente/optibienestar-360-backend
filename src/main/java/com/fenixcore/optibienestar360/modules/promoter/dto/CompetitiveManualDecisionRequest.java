package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionManualDecision.Kind;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionManualDecision.ReasonCategory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Request body for {@code POST /v1/admin/competitive-commission-winners/{ruleUuid}/periods/{periodStart}/decisions}. */
public record CompetitiveManualDecisionRequest(
        @NotNull Kind kind,
        Integer awardPosition,
        @NotNull UUID promoterUuid,
        UUID replacementPromoterUuid,
        boolean excludeFromGroup,
        ReasonCategory reasonCategory,
        @Size(min = 10) String reason
) {}

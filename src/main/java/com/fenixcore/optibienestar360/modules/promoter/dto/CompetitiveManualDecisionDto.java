package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionManualDecision;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionManualDecision.DecisionStatus;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionManualDecision.Kind;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionManualDecision.ReasonCategory;

import java.time.Instant;
import java.util.UUID;

public record CompetitiveManualDecisionDto(
        UUID uuid,
        @Display(Display.Kind.ENUM) Kind kind,
        Integer awardPosition,
        @Display DisplayRef promoter,
        @Display DisplayRef replacedPromoter,
        @Display(Display.Kind.BOOLEAN) boolean excludeFromGroup,
        @Display(Display.Kind.ENUM) ReasonCategory reasonCategory,
        String reason,
        @Display(Display.Kind.DATETIME) Instant decidedAt,
        @Display(Display.Kind.DATETIME) Instant revertedAt,
        String revertReason,
        @Display(Display.Kind.ENUM) DecisionStatus status) {

    public static CompetitiveManualDecisionDto from(CompetitiveCommissionManualDecision decision) {
        return new CompetitiveManualDecisionDto(
                decision.getUuid(),
                decision.getKind(),
                decision.getAwardPosition(),
                DisplayRefs.ref(decision.getPromoter()),
                DisplayRefs.ref(decision.getReplacedPromoter()),
                decision.isExcludeFromGroup(),
                decision.getReasonCategory(),
                decision.getReason(),
                decision.getDecidedAt(),
                decision.getRevertedAt(),
                decision.getRevertReason(),
                DecisionStatus.valueOf(decision.getStatus()));
    }
}

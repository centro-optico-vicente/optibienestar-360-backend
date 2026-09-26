package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionTieCandidate;

import java.math.BigDecimal;
import java.time.Instant;

public record CompetitiveTieCandidateDto(
        @Display DisplayRef promoter,
        BigDecimal metricValue,
        @Display(Display.Kind.DATETIME) Instant achievedAt,
        int metricTransactionCount,
        @Display(Display.Kind.BOOLEAN) boolean selected) {

    public static CompetitiveTieCandidateDto from(CompetitiveCommissionTieCandidate candidate) {
        return new CompetitiveTieCandidateDto(
                DisplayRefs.ref(candidate.getPromoter()),
                candidate.getMetricValue(),
                candidate.getAchievedAt(),
                candidate.getMetricTransactionCount(),
                candidate.isSelected());
    }
}

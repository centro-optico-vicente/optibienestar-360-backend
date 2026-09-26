package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionTie;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CompetitiveTieDto(
        UUID uuid,
        @Display DisplayRef rule,
        LocalDate periodStart,
        LocalDate periodEnd,
        int positionFrom,
        int slots,
        @Display(Display.Kind.ENUM) CompetitiveCommissionTie.TieStatus status,
        String reason,
        @Display(Display.Kind.DATETIME) Instant resolvedAt,
        List<CompetitiveTieCandidateDto> candidates) {

    public static CompetitiveTieDto from(CompetitiveCommissionTie tie) {
        return new CompetitiveTieDto(
                tie.getUuid(),
                DisplayRefs.ref(tie.getRule()),
                tie.getPeriodStart(),
                tie.getPeriodEnd(),
                tie.getPositionFrom(),
                tie.getSlots(),
                CompetitiveCommissionTie.TieStatus.valueOf(tie.getStatus()),
                tie.getReason(),
                tie.getResolvedAt(),
                tie.getCandidates().stream().map(CompetitiveTieCandidateDto::from).toList());
    }
}

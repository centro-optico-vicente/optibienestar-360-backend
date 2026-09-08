package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fenixcore.optibienestar360.core.display.Display;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response body for {@code POST /v1/admin/hierarchy-overrides/re-rate}.
 * Top-level envelope carries the global counters (across both the basis-resync
 * pass and the band re-rate pass — the per-beneficiary breakdown does not
 * separate them, since both ultimately land on the same final {@code amount}
 * per row).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HierarchyOverrideReRatingResponse(
        @Display(Display.Kind.DATE) LocalDate periodStart,
        @Display(Display.Kind.DATE) LocalDate periodEnd,
        @Display(Display.Kind.BOOLEAN) boolean dryRun,

        int totalBeneficiaries,
        int overridesUpdated,
        @Display(Display.Kind.MONEY) BigDecimal totalDeltaAmount,
        String currency,
        @Display(Display.Kind.DATETIME) Instant executedAt,

        List<BeneficiaryReRatingOutcome> perBeneficiary
) {

    public record BeneficiaryReRatingOutcome(
            UUID promoterUuid,
            String promoterCode,
            String promoterDisplayName,
            String category,
            long teamVolumeCount,
            UUID targetTierUuid,
            String targetTierName,
            int overridesChanged,
            @Display(Display.Kind.MONEY) BigDecimal deltaAmount
    ) {}
}

package com.fenixcore.optibienestar360.modules.benefit.dto;

import com.fenixcore.optibienestar360.core.display.Display;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One person's consumption on the affiliate's membership — the holder
 * ({@code beneficiaryUuid} null) or one beneficiary. Backs the usage counter on
 * each family card of the member portal.
 */
public record UsagePersonSummaryDto(
        UUID beneficiaryUuid,
        long usageCount,
        @Display(Display.Kind.DATE) LocalDate lastUsageDate
) {}

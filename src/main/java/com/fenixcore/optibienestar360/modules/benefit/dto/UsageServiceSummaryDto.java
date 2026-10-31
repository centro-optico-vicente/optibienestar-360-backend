package com.fenixcore.optibienestar360.modules.benefit.dto;

import com.fenixcore.optibienestar360.core.display.Display;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One service in the affiliate's consumption summary
 * ({@code GET /v1/me/usage-history/summary}): how many times it was consumed
 * and when last. Uncatalogued usages (no product) group by their category, with
 * {@code productUuid} / {@code productName} null.
 */
public record UsageServiceSummaryDto(
        UUID productUuid,
        String productName,
        String productCategoryName,
        long usageCount,
        @Display(Display.Kind.DATE) LocalDate lastUsageDate
) {}

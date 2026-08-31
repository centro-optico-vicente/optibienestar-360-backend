package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.core.display.Display;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Self-assessment of a promoter's collection performance
 * ({@code GET /v1/promoter/me/collection-score}, v2 PDF 2.b): what share of
 * their active portfolio is up to date. {@code scorePct} = up-to-date /
 * active affiliates × 100 (0 when the portfolio is empty). Basis for a future
 * collection bonus, distinct from the sales bonus.
 *
 * <p>{@code scorePct} carries a localized {@code _Display} sibling (ADR 0014);
 * it's already a 0–100 percentage value, so it uses {@code NUMBER} (matching
 * {@code discountPct} in {@code PublicAllyServiceDto}).</p>
 */
public record CollectionScoreDto(
        UUID promoterUuid,
        int activeAffiliates,
        int upToDate,
        int overdue,
        int withoutMembership,
        @Display(Display.Kind.NUMBER) BigDecimal scorePct
) {}

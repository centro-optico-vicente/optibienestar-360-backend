package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fenixcore.optibienestar360.core.display.Display;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Self-service dashboard for a promoter — the body of {@code GET /v1/promoter/me}
 * (v2 PDF #4). Aggregates the promoter's own book of business:
 *
 * <ul>
 *   <li>{@code activeAffiliates} — active members attributed to this promoter.</li>
 *   <li>Collection health: {@code affiliatesUpToDate} (active membership),
 *       {@code affiliatesOverdue} (suspended/expired), and
 *       {@code affiliatesWithoutMembership} (enrolled but no active membership),
 *       with the full {@code portfolio} for per-affiliate drill-down.</li>
 *   <li>{@code periodCommissions} — commissions earned this calendar month
 *       (non-voided), in {@code periodCurrency}, over [{@code periodStart},
 *       {@code periodEnd}].</li>
 *   <li>{@code leaderboardPosition} — {@code null} for now: the promoter
 *       leaderboard is PDF #5, not yet built.</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PromoterDashboardDto(
        UUID promoterUuid,
        String promoterName,
        String referralCode,

        int activeAffiliates,
        int affiliatesUpToDate,
        int affiliatesOverdue,
        int affiliatesWithoutMembership,

        @Display(Display.Kind.MONEY) BigDecimal periodCommissions,
        String periodCurrency,
        @Display(Display.Kind.DATE) LocalDate periodStart,
        @Display(Display.Kind.DATE) LocalDate periodEnd,

        Integer leaderboardPosition,

        List<PromoterMemberRow> portfolio
) {}

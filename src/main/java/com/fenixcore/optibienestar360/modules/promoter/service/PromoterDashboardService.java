package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterDashboardDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterMemberRow;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Builds the promoter self-service dashboard ({@code GET /v1/promoter/me},
 * v2 PDF #4). Resolves the promoter of the JWT-authenticated user, then
 * aggregates their book of business: portfolio size, collection health
 * (al día / vencida / sin membresía) with per-affiliate drill-down, and
 * commissions earned this calendar month.
 *
 * <p>Membership status → collection bucket: {@code ACTIVE} = al día;
 * {@code SUSPENDED}/{@code EXPIRED} = vencida; no active membership = sin
 * membresía. The leaderboard position is left {@code null} — the promoter
 * leaderboard is PDF #5, not built yet.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromoterDashboardService {

    private static final String CURRENCY = "USD";

    private final PromoterRepository promoterRepository;
    private final MemberRepository memberRepository;
    private final CommissionRepository commissionRepository;

    public PromoterDashboardDto getMyDashboard(UUID actorUserUuid) {
        Promoter promoter = promoterRepository.findActiveByUserUuid(actorUserUuid)
                .orElseThrow(() -> new NoSuchElementException("me.promoter.not_found"));
        return buildDashboard(promoter);
    }

    /** Admin variant of {@link #getMyDashboard} — resolves the promoter by its own uuid
     *  instead of by the JWT-authenticated user, for {@code GET /v1/admin/promoters/{uuid}/portfolio}. */
    public PromoterDashboardDto getDashboardFor(UUID promoterUuid) {
        Promoter promoter = promoterRepository.findByUuid(promoterUuid)
                .orElseThrow(() -> new NoSuchElementException("promoter.not_found"));
        return buildDashboard(promoter);
    }

    private PromoterDashboardDto buildDashboard(Promoter promoter) {
        List<PromoterMemberRow> portfolio = memberRepository.findPromoterPortfolio(promoter.getId());

        int upToDate = 0;
        int overdue = 0;
        int withoutMembership = 0;
        for (PromoterMemberRow row : portfolio) {
            String status = row.membershipStatus();
            if ("ACTIVE".equals(status)) {
                upToDate++;
            } else if ("SUSPENDED".equals(status) || "EXPIRED".equals(status)) {
                overdue++;
            } else {
                // null (no active membership) or any terminal/unexpected value.
                withoutMembership++;
            }
        }

        LocalDate today = LocalDate.now();
        LocalDate periodStart = today.withDayOfMonth(1);
        LocalDate periodEnd = today.withDayOfMonth(today.lengthOfMonth());
        BigDecimal periodCommissions =
                commissionRepository.sumForPromoterInPeriod(promoter.getId(), periodStart, periodEnd);

        return new PromoterDashboardDto(
                promoter.getUuid(),
                promoter.getDisplayName(),
                promoter.getReferralCode(),
                portfolio.size(),
                upToDate,
                overdue,
                withoutMembership,
                periodCommissions,
                CURRENCY,
                periodStart,
                periodEnd,
                null,   // leaderboardPosition — PDF #5, not built yet
                portfolio);
    }
}

package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.util.PeriodStrategies;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterDashboardDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterMemberRow;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.CompetitionType;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.CompetitiveMetric;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.metric.CompetitiveMetricProvider;
import com.fenixcore.optibienestar360.modules.promoter.metric.CompetitiveRankingEngine.Candidate;
import com.fenixcore.optibienestar360.modules.promoter.metric.MetricScope;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CompetitiveCommissionRuleRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
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
 * membresía.</p>
 *
 * <p><b>Leaderboard position (Fase 6, hub plan competitive-commission-rules)</b>:
 * the old per-rank leaderboard (PDF #5) was migrated into a RANKING/{@code
 * COMMISSION_EARNED} competitive rule per period strategy (V167) — {@link
 * #computeLeaderboardPosition} picks the first active one it finds (several
 * can coexist, one per migrated period strategy; a deployment that only
 * ever had one cadence has exactly one), live-ranks every promoter via its
 * own {@link CompetitiveMetricProvider#snapshot}, and returns this
 * promoter's 1-based position — {@code null} when no such rule exists or
 * the promoter has no activity in the current window (never ranked, same
 * as the old leaderboard).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class PromoterDashboardService {

    private static final String CURRENCY = "USD";

    private final PromoterRepository promoterRepository;
    private final MemberRepository memberRepository;
    private final CommissionRepository commissionRepository;
    private final CompetitiveCommissionRuleRepository competitiveRuleRepository;
    private final List<CompetitiveMetricProvider> metricProviders;

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
                computeLeaderboardPosition(promoter),
                portfolio);
    }

    private Integer computeLeaderboardPosition(Promoter promoter) {
        Optional<CompetitiveCommissionRule> rule = competitiveRuleRepository.findByActiveTrue().stream()
                .filter(r -> r.getCompetitionType() == CompetitionType.RANKING
                        && r.getMetric() == CompetitiveMetric.COMMISSION_EARNED)
                .findFirst();
        if (rule.isEmpty()) {
            return null;
        }
        CompetitiveMetricProvider provider = metricProviders.stream()
                .filter(p -> p.metric() == CompetitiveMetric.COMMISSION_EARNED)
                .findFirst().orElse(null);
        if (provider == null) {
            return null;
        }
        try {
            PeriodStrategies.Window window = CompetitiveRuleWindowResolver.resolveWindow(rule.get(), LocalDate.now());
            MetricScope scope = CompetitiveRuleWindowResolver.buildScope(rule.get());
            List<Candidate> ranked = provider.snapshot(window, rule.get().getAchievementDateBasis(), scope).stream()
                    .sorted(Comparator.comparing(Candidate::value).reversed())
                    .toList();
            for (int i = 0; i < ranked.size(); i++) {
                if (ranked.get(i).promoterId().equals(promoter.getId())) {
                    return i + 1;
                }
            }
            return null; // no activity this window — never ranked, same as the old leaderboard
        } catch (RuntimeException ex) {
            log.warn("Leaderboard position computation failed for promoter {}: {}", promoter.getUuid(), ex.getMessage(), ex);
            return null;
        }
    }
}

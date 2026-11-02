package com.fenixcore.optibienestar360.modules.dashboard.service;

import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.exception.NoExchangeRateAvailableException;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.currency.service.CurrencyConversionService;
import com.fenixcore.optibienestar360.modules.dashboard.dto.DashboardActivityDto;
import com.fenixcore.optibienestar360.modules.dashboard.dto.DashboardActivityDto.Status;
import com.fenixcore.optibienestar360.modules.dashboard.dto.DashboardActivityDto.Type;
import com.fenixcore.optibienestar360.modules.dashboard.dto.DashboardChartDto;
import com.fenixcore.optibienestar360.modules.dashboard.dto.DashboardChartDto.LabelKind;
import com.fenixcore.optibienestar360.modules.dashboard.dto.DashboardChartPointDto;
import com.fenixcore.optibienestar360.modules.dashboard.dto.DashboardDto;
import com.fenixcore.optibienestar360.modules.dashboard.dto.DashboardKpiDto;
import com.fenixcore.optibienestar360.modules.dashboard.dto.DashboardSection;
import com.fenixcore.optibienestar360.modules.dashboard.repository.DashboardQueries;
import com.fenixcore.optibienestar360.modules.dashboard.repository.DashboardQueries.LabelledCount;
import com.fenixcore.optibienestar360.modules.dashboard.repository.DashboardQueries.MonthlyAmount;
import com.fenixcore.optibienestar360.modules.dashboard.repository.DashboardQueries.UsageScope;
import com.fenixcore.optibienestar360.modules.dashboard.repository.DashboardQueries.UsageTotals;
import com.fenixcore.optibienestar360.modules.member.entity.Beneficiary;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.BeneficiaryRepository;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterMemberRow;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Builds the home dashboard ({@code GET /v1/me/dashboard}) from the caller's
 * authorities. The endpoint has no permission of its own: each block is added
 * only when the caller holds the permissions that already protect the same
 * data elsewhere, so the panel never reveals more than the screens it
 * summarizes.
 *
 * <ul>
 *   <li>{@link DashboardSection#MANAGEMENT} — program-wide figures, each one
 *       gated by its own "view all" permission (memberships, collections,
 *       usages, affiliation requests).</li>
 *   <li>{@link DashboardSection#ADVISOR} — {@code PROMOTER_VIEW_OWN} and the
 *       caller is an active promoter; reuses the portfolio query and payment
 *       status of {@code GET /v1/me/advisor}.</li>
 *   <li>{@link DashboardSection#ALLY} — {@code ALLY_VIEW_OWN} and the caller
 *       operates at least one active ally.</li>
 *   <li>{@link DashboardSection#MEMBER} — {@code MEMBER_VIEW_OWN}: the holder
 *       sees the whole family; a beneficiary with access (no member record of
 *       their own) sees only their own usages.</li>
 * </ul>
 *
 * <p>Every figure is an SQL aggregate ({@link DashboardQueries}); charts cover
 * the last {@value #MONTHS} calendar months including the current one, with
 * empty months filled with zero. Collections are reported in USD: amounts in
 * other currencies are converted with the rate in force at the end of their
 * month ({@link CurrencyConversionService}); a missing rate drops that amount
 * from the total instead of failing the panel.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class DashboardService {

    static final int MONTHS = 6;
    static final int ACTIVITY_LIMIT = 8;
    static final int TOP_SERVICES = 6;
    static final String REPORT_CURRENCY = "USD";

    private static final List<String> MEMBERSHIP_STATUSES = List.of("ACTIVE", "SUSPENDED", "EXPIRED", "CANCELED");
    private static final List<String> PAYMENT_STATUSES = List.of("UP_TO_DATE", "DUE", "OVERDUE", "NO_MEMBERSHIP");

    private final DashboardQueries queries;
    private final PromoterRepository promoterRepository;
    private final MemberRepository memberRepository;
    private final MembershipRepository membershipRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final AllyUserRepository allyUserRepository;
    private final CurrencyRepository currencyRepository;
    private final CurrencyConversionService conversionService;

    /** Overridable in tests; production uses the app timezone. */
    private Clock clock = Clock.system(AppTimeZone.ZONE);

    void setClock(Clock clock) {
        this.clock = clock;
    }

    public DashboardDto getMyDashboard(UUID userUuid, Collection<String> authorities) {
        Set<String> perms = Set.copyOf(authorities);
        Builder b = new Builder(LocalDate.now(clock));

        buildManagement(b, perms);
        if (perms.contains("PROMOTER_VIEW_OWN")) {
            promoterRepository.findActiveByUserUuid(userUuid).ifPresent(p -> buildAdvisor(b, p));
        }
        if (perms.contains("ALLY_VIEW_OWN")) {
            List<AllyUser> allyUsers = allyUserRepository.findActiveByUserUuid(userUuid);
            if (!allyUsers.isEmpty()) {
                buildAlly(b, allyUsers);
            }
        }
        if (perms.contains("MEMBER_VIEW_OWN")) {
            Optional<Member> member = memberRepository.findByUserUuid(userUuid).filter(Member::isActive);
            if (member.isPresent()) {
                buildMember(b, member.get());
            } else {
                beneficiaryRepository.findByUser_UuidAndActiveTrue(userUuid).ifPresent(ben -> buildBeneficiary(b, ben));
            }
        }
        return b.build();
    }

    // ─── Management ───────────────────────────────────────────────────────────

    private void buildManagement(Builder b, Set<String> perms) {
        DashboardSection s = DashboardSection.MANAGEMENT;
        boolean memberships = perms.contains("MEMBERSHIP_VIEW_ALL");
        boolean members = perms.contains("MEMBER_VIEW_ALL");
        boolean collections = perms.contains("COLLECTION_VIEW_ALL") || perms.contains("PAYMENT_VIEW_ALL");
        boolean usages = perms.contains("ALLY_VIEW_ALL") || members;
        boolean requests = perms.contains("AFFILIATION_REQUEST_VIEW_ALL");
        if (!(memberships || members || collections || usages || requests)) {
            return;
        }
        b.sections.add(s);

        if (memberships || members) {
            b.kpis.add(DashboardKpiDto.number(s, "activeMembers", queries.countMembersWithActiveMembership()));
        }
        Map<String, BigDecimal> revenue = Map.of();
        if (collections) {
            revenue = revenueByMonth(b.firstMonth.atDay(1));
            b.kpis.add(DashboardKpiDto.money(s, "monthlyRevenue",
                    revenue.getOrDefault(b.currentMonth.toString(), BigDecimal.ZERO),
                    revenue.getOrDefault(b.currentMonth.minusMonths(1).toString(), BigDecimal.ZERO),
                    REPORT_CURRENCY));
        }
        if (usages) {
            UsageTotals current = queries.usageTotals(UsageScope.ALL, b.monthStart(0), b.monthEnd(0));
            UsageTotals previous = queries.usageTotals(UsageScope.ALL, b.monthStart(1), b.monthEnd(1));
            b.kpis.add(DashboardKpiDto.number(s, "allyUsages", current.usages(), previous.usages()));
        }
        if (collections) {
            b.kpis.add(DashboardKpiDto.number(s, "pendingPayments", queries.countPendingCollections()));
        }
        if (requests) {
            b.kpis.add(DashboardKpiDto.number(s, "pendingAffiliationRequests",
                    queries.countPendingAffiliationRequests()));
        }

        if (members) {
            b.charts.add(DashboardChartDto.counts(s, "newMembersByMonth", DashboardChartDto.Type.BAR,
                    LabelKind.MONTH, b.monthlyPoints(queries.countNewMembersByMonth(b.firstMonth.atDay(1), null))));
        }
        if (memberships) {
            b.charts.add(DashboardChartDto.counts(s, "membershipsByStatus", DashboardChartDto.Type.DONUT,
                    LabelKind.ENUM, enumPoints(MEMBERSHIP_STATUSES, queries.countMembershipsByStatus())));
        }
        if (collections) {
            Map<String, BigDecimal> byMonth = revenue;
            List<DashboardChartPointDto> points = b.months().stream()
                    .map(m -> DashboardChartPointDto.of(m, byMonth.getOrDefault(m, BigDecimal.ZERO)))
                    .toList();
            b.charts.add(new DashboardChartDto("revenueByMonth", s, DashboardChartDto.Type.LINE, LabelKind.MONTH,
                    DashboardKpiDto.Format.MONEY, REPORT_CURRENCY, points));
        }

        if (collections) {
            queries.findRecentPayments(null, null, ACTIVITY_LIMIT).forEach(e -> b.activity.add(paymentActivity(s, e)));
        }
        if (usages) {
            queries.findRecentUsages(UsageScope.ALL, ACTIVITY_LIMIT)
                    .forEach(e -> b.activity.add(usageActivity(s, e, true)));
        }
        if (requests) {
            queries.findRecentAffiliationRequests(null, ACTIVITY_LIMIT)
                    .forEach(e -> b.activity.add(requestActivity(s, e)));
        }
    }

    /** Approved collections per {@code yyyy-MM}, in USD. */
    private Map<String, BigDecimal> revenueByMonth(LocalDate from) {
        Instant fromInstant = from.atStartOfDay(AppTimeZone.ZONE).toInstant();
        Map<String, BigDecimal> totals = new HashMap<>();
        Map<String, Optional<Currency>> currencies = new HashMap<>();
        Currency usd = null;
        for (MonthlyAmount row : queries.sumApprovedCollectionsByMonth(fromInstant)) {
            BigDecimal amount = row.amount() == null ? BigDecimal.ZERO : row.amount();
            if (!REPORT_CURRENCY.equals(row.currencyCode())) {
                if (usd == null) {
                    usd = currencyRepository.findByCode(REPORT_CURRENCY).orElse(null);
                }
                amount = toUsd(amount, row, usd, currencies);
                if (amount == null) {
                    continue;
                }
            }
            totals.merge(row.month(), amount, BigDecimal::add);
        }
        totals.replaceAll((k, v) -> v.setScale(2, RoundingMode.HALF_UP));
        return totals;
    }

    private BigDecimal toUsd(BigDecimal amount, MonthlyAmount row, Currency usd,
                             Map<String, Optional<Currency>> cache) {
        Optional<Currency> from = cache.computeIfAbsent(row.currencyCode(), currencyRepository::findByCode);
        if (usd == null || from.isEmpty()) {
            return null;
        }
        Instant monthEnd = YearMonth.parse(row.month()).plusMonths(1).atDay(1)
                .atStartOfDay(AppTimeZone.ZONE).toInstant().minusSeconds(1);
        Instant asOf = monthEnd.isAfter(clock.instant()) ? clock.instant() : monthEnd;
        try {
            return conversionService.convert(amount, from.get(), usd, asOf).convertedAmount();
        } catch (NoExchangeRateAvailableException ex) {
            log.warn("Dashboard revenue: no {}->USD rate for {}; amount left out", row.currencyCode(), row.month());
            return null;
        }
    }

    // ─── Advisor ──────────────────────────────────────────────────────────────

    private void buildAdvisor(Builder b, Promoter promoter) {
        DashboardSection s = DashboardSection.ADVISOR;
        b.sections.add(s);

        List<PromoterMemberRow> portfolio = memberRepository.findPromoterPortfolio(promoter.getId());
        Map<String, Long> byStatus = new LinkedHashMap<>();
        PAYMENT_STATUSES.forEach(st -> byStatus.put(st, 0L));
        for (PromoterMemberRow row : portfolio) {
            byStatus.merge(row.paymentStatus(), 1L, Long::sum);
        }
        Map<String, Long> newByMonth = queries.countNewMembersByMonth(b.firstMonth.atDay(1), promoter.getId());

        b.kpis.add(DashboardKpiDto.number(s, "portfolioMembers", portfolio.size()));
        b.kpis.add(DashboardKpiDto.number(s, "portfolioUpToDate", byStatus.get("UP_TO_DATE")));
        b.kpis.add(DashboardKpiDto.number(s, "portfolioDue", byStatus.get("DUE")));
        b.kpis.add(DashboardKpiDto.number(s, "portfolioOverdue", byStatus.get("OVERDUE")));
        b.kpis.add(DashboardKpiDto.number(s, "portfolioNewThisMonth",
                newByMonth.getOrDefault(b.currentMonth.toString(), 0L),
                newByMonth.getOrDefault(b.currentMonth.minusMonths(1).toString(), 0L)));

        b.charts.add(DashboardChartDto.counts(s, "portfolioNewByMonth", DashboardChartDto.Type.BAR,
                LabelKind.MONTH, b.monthlyPoints(newByMonth)));
        b.charts.add(DashboardChartDto.counts(s, "portfolioPaymentStatus", DashboardChartDto.Type.DONUT,
                LabelKind.ENUM, enumPoints(PAYMENT_STATUSES, byStatus)));

        queries.findRecentPayments(promoter.getId(), null, ACTIVITY_LIMIT)
                .forEach(e -> b.activity.add(paymentActivity(s, e)));
        queries.findRecentEnrollments(promoter.getId(), ACTIVITY_LIMIT)
                .forEach(e -> b.activity.add(new DashboardActivityDto(Type.MEMBER_ENROLLED, s, e.memberName(),
                        e.planName(), null, null, e.occurredAt(), Status.SUCCESS)));
        queries.findRecentAffiliationRequests(promoter.getId(), ACTIVITY_LIMIT)
                .forEach(e -> b.activity.add(requestActivity(s, e)));
    }

    // ─── Ally ─────────────────────────────────────────────────────────────────

    private void buildAlly(Builder b, List<AllyUser> allyUsers) {
        DashboardSection s = DashboardSection.ALLY;
        b.sections.add(s);

        List<Ally> allies = allyUsers.stream().map(AllyUser::getAlly).distinct().toList();
        UsageScope scope = UsageScope.ofAllies(allies.stream().map(Ally::getId).toList());

        UsageTotals current = queries.usageTotals(scope, b.monthStart(0), b.monthEnd(0));
        UsageTotals previous = queries.usageTotals(scope, b.monthStart(1), b.monthEnd(1));
        b.kpis.add(DashboardKpiDto.number(s, "allyMonthUsages", current.usages(), previous.usages()));
        b.kpis.add(DashboardKpiDto.number(s, "allyMembersServed", current.peopleServed(), previous.peopleServed()));
        b.kpis.add(allyRating(s, allies));

        b.charts.add(DashboardChartDto.counts(s, "allyUsagesByMonth", DashboardChartDto.Type.BAR, LabelKind.MONTH,
                b.monthlyPoints(queries.countUsagesByMonth(scope, b.firstMonth.atDay(1)))));
        b.charts.add(DashboardChartDto.counts(s, "allyUsagesByService", DashboardChartDto.Type.BAR, LabelKind.TEXT,
                labelledPoints(queries.topServices(scope, b.firstMonth.atDay(1), TOP_SERVICES))));

        queries.findRecentUsages(scope, ACTIVITY_LIMIT).forEach(e -> b.activity.add(usageActivity(s, e, false)));
    }

    /** Weighted average of the allies' ratings (V224 aggregates); null value when nobody rated yet. */
    static DashboardKpiDto allyRating(DashboardSection s, List<Ally> allies) {
        long count = 0;
        BigDecimal weighted = BigDecimal.ZERO;
        for (Ally ally : allies) {
            if (ally.getRatingAvg() != null && ally.getRatingCount() > 0) {
                count += ally.getRatingCount();
                weighted = weighted.add(ally.getRatingAvg().multiply(BigDecimal.valueOf(ally.getRatingCount())));
            }
        }
        BigDecimal average = count == 0 ? null : weighted.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        return DashboardKpiDto.rating(s, "allyRating", average, count);
    }

    // ─── Member / beneficiary ─────────────────────────────────────────────────

    private void buildMember(Builder b, Member member) {
        DashboardSection s = DashboardSection.MEMBER;
        b.sections.add(s);

        Optional<Membership> membership = membershipRepository.findFirstByMemberIdAndActiveTrue(member.getId());
        b.kpis.add(DashboardKpiDto.text(s, "membershipStatus", membership.map(Membership::getStatus).orElse("NONE")));
        b.kpis.add(DashboardKpiDto.date(s, "nextDueDate", membership.map(Membership::getNextDueDate)
                .map(LocalDate::toString).orElse(null)));
        b.kpis.add(DashboardKpiDto.number(s, "beneficiaries",
                beneficiaryRepository.countByMemberIdAndActiveTrue(member.getId())));
        UsageScope scope = UsageScope.ofMember(member.getId());
        UsageTotals current = queries.usageTotals(scope, b.monthStart(0), b.monthEnd(0));
        UsageTotals previous = queries.usageTotals(scope, b.monthStart(1), b.monthEnd(1));
        b.kpis.add(DashboardKpiDto.number(s, "memberMonthUsages", current.usages(), previous.usages()));

        b.charts.add(DashboardChartDto.counts(s, "memberUsagesByPerson", DashboardChartDto.Type.DONUT,
                LabelKind.TEXT, labelledPoints(queries.countUsagesByPerson(member.getId()))));
        b.charts.add(DashboardChartDto.counts(s, "memberUsagesByMonth", DashboardChartDto.Type.LINE,
                LabelKind.MONTH, b.monthlyPoints(queries.countUsagesByMonth(scope, b.firstMonth.atDay(1)))));

        queries.findRecentPayments(null, member.getPerson().getId(), ACTIVITY_LIMIT)
                .forEach(e -> b.activity.add(paymentActivity(s, e)));
        queries.findRecentUsages(scope, ACTIVITY_LIMIT).forEach(e -> b.activity.add(usageActivity(s, e, true)));
    }

    /** A beneficiary with access: their own coverage status and usages only — never the holder's payments. */
    private void buildBeneficiary(Builder b, Beneficiary beneficiary) {
        DashboardSection s = DashboardSection.MEMBER;
        b.sections.add(s);

        Optional<Membership> membership =
                membershipRepository.findFirstByMemberIdAndActiveTrue(beneficiary.getMember().getId());
        b.kpis.add(DashboardKpiDto.text(s, "membershipStatus", membership.map(Membership::getStatus).orElse("NONE")));
        UsageScope scope = UsageScope.ofBeneficiary(beneficiary.getId());
        UsageTotals current = queries.usageTotals(scope, b.monthStart(0), b.monthEnd(0));
        UsageTotals previous = queries.usageTotals(scope, b.monthStart(1), b.monthEnd(1));
        b.kpis.add(DashboardKpiDto.number(s, "memberMonthUsages", current.usages(), previous.usages()));
        UsageTotals total = queries.usageTotals(scope, LocalDate.of(2000, 1, 1), b.today);
        b.kpis.add(DashboardKpiDto.number(s, "memberTotalUsages", total.usages()));

        b.charts.add(DashboardChartDto.counts(s, "memberUsagesByMonth", DashboardChartDto.Type.LINE,
                LabelKind.MONTH, b.monthlyPoints(queries.countUsagesByMonth(scope, b.firstMonth.atDay(1)))));
        b.charts.add(DashboardChartDto.counts(s, "memberUsagesByService", DashboardChartDto.Type.BAR,
                LabelKind.TEXT, labelledPoints(queries.topServices(scope, b.firstMonth.atDay(1), TOP_SERVICES))));

        queries.findRecentUsages(scope, ACTIVITY_LIMIT).forEach(e -> b.activity.add(usageActivity(s, e, true)));
    }

    // ─── Activity mapping ─────────────────────────────────────────────────────

    static DashboardActivityDto paymentActivity(DashboardSection s, DashboardQueries.PaymentEvent e) {
        Type type = switch (e.status()) {
            case "APPROVED" -> Type.PAYMENT_APPROVED;
            case "REJECTED" -> Type.PAYMENT_REJECTED;
            default -> Type.PAYMENT_REGISTERED;
        };
        return new DashboardActivityDto(type, s, e.personName(), e.planName(), e.amount(), e.currencyCode(),
                e.occurredAt(), statusOf(type));
    }

    /** {@code withAlly}: subtitle "service · ally" (an ally operator already knows the ally). */
    static DashboardActivityDto usageActivity(DashboardSection s, DashboardQueries.UsageEvent e, boolean withAlly) {
        Type type = "REVERSED".equals(e.status()) ? Type.USAGE_REVERSED : Type.USAGE_REGISTERED;
        String subtitle = withAlly && e.allyName() != null
                ? (e.serviceName() == null ? e.allyName() : e.serviceName() + " · " + e.allyName())
                : e.serviceName();
        return new DashboardActivityDto(type, s, e.personName(), subtitle, null, null, e.occurredAt(),
                statusOf(type));
    }

    static DashboardActivityDto requestActivity(DashboardSection s, DashboardQueries.AffiliationRequestEvent e) {
        Type type = switch (e.status()) {
            case "APPROVED" -> Type.AFFILIATION_REQUEST_APPROVED;
            case "REJECTED" -> Type.AFFILIATION_REQUEST_REJECTED;
            default -> Type.AFFILIATION_REQUEST_RECEIVED;
        };
        return new DashboardActivityDto(type, s, e.applicantName(), e.planName(), null, null, e.occurredAt(),
                statusOf(type));
    }

    private static Status statusOf(Type type) {
        return switch (type) {
            case PAYMENT_REGISTERED, AFFILIATION_REQUEST_RECEIVED -> Status.PENDING;
            case PAYMENT_REJECTED, USAGE_REVERSED, AFFILIATION_REQUEST_REJECTED -> Status.ERROR;
            default -> Status.SUCCESS;
        };
    }

    // ─── Series helpers ───────────────────────────────────────────────────────

    private static List<DashboardChartPointDto> enumPoints(List<String> order, Map<String, Long> counts) {
        List<DashboardChartPointDto> points = new ArrayList<>();
        order.forEach(k -> points.add(DashboardChartPointDto.of(k, counts.getOrDefault(k, 0L))));
        counts.forEach((k, v) -> {
            if (k != null && !order.contains(k)) {
                points.add(DashboardChartPointDto.of(k, v));
            }
        });
        return points;
    }

    private static List<DashboardChartPointDto> labelledPoints(List<LabelledCount> rows) {
        return rows.stream().map(r -> DashboardChartPointDto.labelled(r.key(), r.label(), r.count())).toList();
    }

    /** Accumulates one response; sections keep the {@link DashboardSection} order. */
    private static final class Builder {
        final LocalDate today;
        final YearMonth currentMonth;
        final YearMonth firstMonth;
        final EnumSet<DashboardSection> sections = EnumSet.noneOf(DashboardSection.class);
        final List<DashboardKpiDto> kpis = new ArrayList<>();
        final List<DashboardChartDto> charts = new ArrayList<>();
        final List<DashboardActivityDto> activity = new ArrayList<>();

        Builder(LocalDate today) {
            this.today = today;
            this.currentMonth = YearMonth.from(today);
            this.firstMonth = currentMonth.minusMonths(MONTHS - 1L);
        }

        LocalDate monthStart(int monthsAgo) {
            return currentMonth.minusMonths(monthsAgo).atDay(1);
        }

        LocalDate monthEnd(int monthsAgo) {
            return currentMonth.minusMonths(monthsAgo).atEndOfMonth();
        }

        List<String> months() {
            List<String> months = new ArrayList<>(MONTHS);
            for (int i = 0; i < MONTHS; i++) {
                months.add(firstMonth.plusMonths(i).toString());
            }
            return months;
        }

        List<DashboardChartPointDto> monthlyPoints(Map<String, Long> counts) {
            return months().stream().map(m -> DashboardChartPointDto.of(m, counts.getOrDefault(m, 0L))).toList();
        }

        DashboardDto build() {
            List<DashboardActivityDto> feed = activity.stream()
                    .filter(a -> a.occurredAt() != null)
                    .sorted(Comparator.comparing(DashboardActivityDto::occurredAt).reversed())
                    .distinct()
                    .limit(ACTIVITY_LIMIT)
                    .toList();
            return new DashboardDto(currentMonth.atDay(1), List.copyOf(sections), kpis, charts, feed);
        }
    }
}

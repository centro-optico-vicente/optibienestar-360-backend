package com.fenixcore.optibienestar360.modules.dashboard.service;

import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.exception.NoExchangeRateAvailableException;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.currency.service.CurrencyConversionService;
import com.fenixcore.optibienestar360.modules.currency.service.CurrencyConversionService.ConversionResult;
import com.fenixcore.optibienestar360.modules.dashboard.dto.DashboardActivityDto;
import com.fenixcore.optibienestar360.modules.dashboard.dto.DashboardChartDto;
import com.fenixcore.optibienestar360.modules.dashboard.dto.DashboardChartPointDto;
import com.fenixcore.optibienestar360.modules.dashboard.dto.DashboardDto;
import com.fenixcore.optibienestar360.modules.dashboard.dto.DashboardKpiDto;
import com.fenixcore.optibienestar360.modules.dashboard.dto.DashboardSection;
import com.fenixcore.optibienestar360.modules.dashboard.repository.DashboardQueries;
import com.fenixcore.optibienestar360.modules.dashboard.repository.DashboardQueries.AffiliationRequestEvent;
import com.fenixcore.optibienestar360.modules.dashboard.repository.DashboardQueries.EnrollmentEvent;
import com.fenixcore.optibienestar360.modules.dashboard.repository.DashboardQueries.LabelledCount;
import com.fenixcore.optibienestar360.modules.dashboard.repository.DashboardQueries.MonthlyAmount;
import com.fenixcore.optibienestar360.modules.dashboard.repository.DashboardQueries.PaymentEvent;
import com.fenixcore.optibienestar360.modules.dashboard.repository.DashboardQueries.UsageEvent;
import com.fenixcore.optibienestar360.modules.dashboard.repository.DashboardQueries.UsageScope;
import com.fenixcore.optibienestar360.modules.dashboard.repository.DashboardQueries.UsageTotals;
import com.fenixcore.optibienestar360.modules.member.entity.Beneficiary;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.BeneficiaryRepository;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterMemberRow;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DashboardService} — the {@code GET /v1/me/dashboard}
 * assembly. Locks which blocks each set of authorities unlocks, the month
 * buckets (6 months, gaps filled with zero), the USD conversion of
 * collections, the advisor payment-status donut and the beneficiary scope.
 * Today is pinned to 2026-11-15 in the app timezone.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UsageTotals NO_USAGES = new UsageTotals(0, 0);

    @Mock private DashboardQueries queries;
    @Mock private PromoterRepository promoterRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private BeneficiaryRepository beneficiaryRepository;
    @Mock private AllyUserRepository allyUserRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private CurrencyConversionService conversionService;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(queries, promoterRepository, memberRepository, membershipRepository,
                beneficiaryRepository, allyUserRepository, currencyRepository, conversionService);
        service.setClock(Clock.fixed(Instant.parse("2026-11-15T15:00:00Z"), AppTimeZone.ZONE));
        when(queries.usageTotals(any(), any(), any())).thenReturn(NO_USAGES);
        when(memberRepository.findByUserUuid(USER)).thenReturn(Optional.empty());
        when(beneficiaryRepository.findByUser_UuidAndActiveTrue(USER)).thenReturn(Optional.empty());
    }

    private static Map<String, DashboardKpiDto> kpis(DashboardDto dto) {
        return dto.kpis().stream().collect(Collectors.toMap(DashboardKpiDto::key, Function.identity()));
    }

    private static DashboardChartDto chart(DashboardDto dto, String key) {
        return dto.charts().stream().filter(c -> c.key().equals(key)).findFirst().orElseThrow();
    }

    private static Map<String, BigDecimal> values(DashboardChartDto chart) {
        return chart.points().stream().collect(Collectors.toMap(DashboardChartPointDto::key,
                DashboardChartPointDto::value, (a, b) -> a, java.util.LinkedHashMap::new));
    }

    private static Currency currency(long id, String code) {
        Currency c = new Currency();
        c.setId(id);
        c.setCode(code);
        return c;
    }

    @Test
    void noRelevantAuthorities_returnsEmptyDashboard() {
        DashboardDto dto = service.getMyDashboard(USER, List.of("PROFILE_VIEW_OWN"));

        assertThat(dto.sections()).isEmpty();
        assertThat(dto.kpis()).isEmpty();
        assertThat(dto.charts()).isEmpty();
        assertThat(dto.activity()).isEmpty();
        assertThat(dto.periodStart()).isEqualTo(LocalDate.of(2026, 11, 1));
        verifyNoInteractions(queries);
    }

    @Test
    void management_buildsKpisChartsAndConvertsRevenueToUsd() {
        Currency usd = currency(1, "USD");
        Currency ves = currency(2, "VES");
        when(currencyRepository.findByCode("USD")).thenReturn(Optional.of(usd));
        when(currencyRepository.findByCode("VES")).thenReturn(Optional.of(ves));
        when(conversionService.convert(eq(new BigDecimal("3600.00")), eq(ves), eq(usd), any()))
                .thenReturn(new ConversionResult(new BigDecimal("3600.00"), new BigDecimal("10.00"),
                        new BigDecimal("0.0027"), LocalDate.of(2026, 11, 14)));
        when(queries.sumApprovedCollectionsByMonth(any())).thenReturn(List.of(
                new MonthlyAmount("2026-11", "USD", new BigDecimal("40.00")),
                new MonthlyAmount("2026-11", "VES", new BigDecimal("3600.00")),
                new MonthlyAmount("2026-10", "USD", new BigDecimal("25.00"))));
        when(queries.countMembersWithActiveMembership()).thenReturn(12L);
        when(queries.countPendingCollections()).thenReturn(3L);
        when(queries.countPendingAffiliationRequests()).thenReturn(2L);
        when(queries.usageTotals(eq(UsageScope.ALL), eq(LocalDate.of(2026, 11, 1)), any()))
                .thenReturn(new UsageTotals(7, 5));
        when(queries.usageTotals(eq(UsageScope.ALL), eq(LocalDate.of(2026, 10, 1)), any()))
                .thenReturn(new UsageTotals(4, 4));
        when(queries.countNewMembersByMonth(LocalDate.of(2026, 6, 1), null))
                .thenReturn(Map.of("2026-07", 2L, "2026-11", 5L));
        when(queries.countMembershipsByStatus()).thenReturn(Map.of("ACTIVE", 12L, "SUSPENDED", 1L));
        Instant t = Instant.parse("2026-11-14T10:00:00Z");
        when(queries.findRecentPayments(isNull(), isNull(), anyInt())).thenReturn(List.of(
                new PaymentEvent("APPROVED", "Ana", "Plan Familiar", BigDecimal.TEN, "USD", t.minusSeconds(60)),
                new PaymentEvent("PENDING", "Luis", null, BigDecimal.ONE, "USD", t.plusSeconds(60))));
        when(queries.findRecentUsages(eq(UsageScope.ALL), anyInt())).thenReturn(List.of(
                new UsageEvent("REGISTERED", "Mia", "Óptica Centro", "Consulta", t)));
        when(queries.findRecentAffiliationRequests(isNull(), anyInt())).thenReturn(List.of(
                new AffiliationRequestEvent("PENDING", "Carlos Rivas", "Plan Básico", t.minusSeconds(3600))));

        DashboardDto dto = service.getMyDashboard(USER, List.of("MEMBERSHIP_VIEW_ALL", "MEMBER_VIEW_ALL",
                "COLLECTION_VIEW_ALL", "AFFILIATION_REQUEST_VIEW_ALL"));

        assertThat(dto.sections()).containsExactly(DashboardSection.MANAGEMENT);
        Map<String, DashboardKpiDto> k = kpis(dto);
        assertThat(k.get("activeMembers").value()).isEqualByComparingTo("12");
        assertThat(k.get("monthlyRevenue").value()).isEqualByComparingTo("50.00");
        assertThat(k.get("monthlyRevenue").previousValue()).isEqualByComparingTo("25.00");
        assertThat(k.get("monthlyRevenue").currency()).isEqualTo("USD");
        assertThat(k.get("allyUsages").value()).isEqualByComparingTo("7");
        assertThat(k.get("allyUsages").previousValue()).isEqualByComparingTo("4");
        assertThat(k.get("pendingPayments").value()).isEqualByComparingTo("3");
        assertThat(k.get("pendingAffiliationRequests").value()).isEqualByComparingTo("2");

        assertThat(values(chart(dto, "newMembersByMonth")))
                .containsExactly(
                        Map.entry("2026-06", BigDecimal.ZERO), Map.entry("2026-07", BigDecimal.valueOf(2)),
                        Map.entry("2026-08", BigDecimal.ZERO), Map.entry("2026-09", BigDecimal.ZERO),
                        Map.entry("2026-10", BigDecimal.ZERO), Map.entry("2026-11", BigDecimal.valueOf(5)));
        assertThat(values(chart(dto, "membershipsByStatus")).keySet())
                .containsExactly("ACTIVE", "SUSPENDED", "EXPIRED", "CANCELED");
        DashboardChartDto revenue = chart(dto, "revenueByMonth");
        assertThat(revenue.format()).isEqualTo(DashboardKpiDto.Format.MONEY);
        assertThat(values(revenue).get("2026-11")).isEqualByComparingTo("50.00");

        assertThat(dto.activity()).extracting(DashboardActivityDto::type).containsExactly(
                DashboardActivityDto.Type.PAYMENT_REGISTERED,
                DashboardActivityDto.Type.USAGE_REGISTERED,
                DashboardActivityDto.Type.PAYMENT_APPROVED,
                DashboardActivityDto.Type.AFFILIATION_REQUEST_RECEIVED);
        assertThat(dto.activity().get(0).status()).isEqualTo(DashboardActivityDto.Status.PENDING);
        assertThat(dto.activity().get(1).subtitle()).isEqualTo("Consulta · Óptica Centro");
    }

    @Test
    void management_missingExchangeRate_leavesAmountOutInsteadOfFailing() {
        Currency usd = currency(1, "USD");
        Currency ves = currency(2, "VES");
        when(currencyRepository.findByCode("USD")).thenReturn(Optional.of(usd));
        when(currencyRepository.findByCode("VES")).thenReturn(Optional.of(ves));
        when(conversionService.convert(any(), any(), any(), any()))
                .thenThrow(new NoExchangeRateAvailableException("exchange_rate.not_available:VES->USD"));
        when(queries.sumApprovedCollectionsByMonth(any())).thenReturn(List.of(
                new MonthlyAmount("2026-11", "USD", new BigDecimal("40.00")),
                new MonthlyAmount("2026-11", "VES", new BigDecimal("3600.00"))));

        DashboardDto dto = service.getMyDashboard(USER, List.of("PAYMENT_VIEW_ALL"));

        assertThat(kpis(dto).get("monthlyRevenue").value()).isEqualByComparingTo("40.00");
        assertThat(kpis(dto)).doesNotContainKey("activeMembers");
    }

    @Test
    void advisor_bucketsPortfolioByPaymentStatus() {
        Promoter promoter = new Promoter();
        promoter.setId(77L);
        when(promoterRepository.findActiveByUserUuid(USER)).thenReturn(Optional.of(promoter));
        LocalDate future = LocalDate.now().plusDays(10);
        LocalDate past = LocalDate.now().minusDays(3);
        when(memberRepository.findPromoterPortfolio(77L)).thenReturn(List.of(
                new PromoterMemberRow(UUID.randomUUID(), "A", "ACTIVE", future, BigDecimal.ONE, null, false),
                new PromoterMemberRow(UUID.randomUUID(), "B", "ACTIVE", past, BigDecimal.ONE, null, false),
                new PromoterMemberRow(UUID.randomUUID(), "C", "SUSPENDED", past, BigDecimal.ONE, null, false),
                new PromoterMemberRow(UUID.randomUUID(), "D", "EXPIRED", past, BigDecimal.ONE, null, false),
                new PromoterMemberRow(UUID.randomUUID(), "E", null, null, null, null, false)));
        when(queries.countNewMembersByMonth(LocalDate.of(2026, 6, 1), 77L))
                .thenReturn(Map.of("2026-11", 2L, "2026-10", 1L));
        when(queries.findRecentEnrollments(eq(77L), anyInt())).thenReturn(List.of(
                new EnrollmentEvent("E", "Plan Básico", Instant.parse("2026-11-10T12:00:00Z"))));

        DashboardDto dto = service.getMyDashboard(USER, List.of("PROMOTER_VIEW_OWN"));

        assertThat(dto.sections()).containsExactly(DashboardSection.ADVISOR);
        Map<String, DashboardKpiDto> k = kpis(dto);
        assertThat(k.get("portfolioMembers").value()).isEqualByComparingTo("5");
        assertThat(k.get("portfolioUpToDate").value()).isEqualByComparingTo("1");
        assertThat(k.get("portfolioDue").value()).isEqualByComparingTo("1");
        assertThat(k.get("portfolioOverdue").value()).isEqualByComparingTo("2");
        assertThat(k.get("portfolioNewThisMonth").value()).isEqualByComparingTo("2");
        assertThat(k.get("portfolioNewThisMonth").previousValue()).isEqualByComparingTo("1");
        assertThat(values(chart(dto, "portfolioPaymentStatus"))).containsExactly(
                Map.entry("UP_TO_DATE", BigDecimal.ONE), Map.entry("DUE", BigDecimal.ONE),
                Map.entry("OVERDUE", BigDecimal.valueOf(2)), Map.entry("NO_MEMBERSHIP", BigDecimal.ONE));
        assertThat(chart(dto, "portfolioNewByMonth").points()).hasSize(6);
        assertThat(dto.activity()).extracting(DashboardActivityDto::type)
                .containsExactly(DashboardActivityDto.Type.MEMBER_ENROLLED);
        verify(queries).findRecentPayments(77L, null, DashboardService.ACTIVITY_LIMIT);
        verify(queries).findRecentAffiliationRequests(77L, DashboardService.ACTIVITY_LIMIT);
    }

    @Test
    void advisorPermission_withoutPromoterRecord_addsNoSection() {
        when(promoterRepository.findActiveByUserUuid(USER)).thenReturn(Optional.empty());

        DashboardDto dto = service.getMyDashboard(USER, List.of("PROMOTER_VIEW_OWN"));

        assertThat(dto.sections()).isEmpty();
        verify(memberRepository, never()).findPromoterPortfolio(any());
    }

    @Test
    void ally_scopesUsagesToOwnAlliesAndWeightsRating() {
        Ally a1 = new Ally();
        a1.setId(5L);
        a1.setRatingAvg(new BigDecimal("4.00"));
        a1.setRatingCount(3);
        Ally a2 = new Ally();
        a2.setId(6L);
        a2.setRatingAvg(new BigDecimal("5.00"));
        a2.setRatingCount(1);
        AllyUser u1 = new AllyUser();
        u1.setAlly(a1);
        AllyUser u2 = new AllyUser();
        u2.setAlly(a2);
        when(allyUserRepository.findActiveByUserUuid(USER)).thenReturn(List.of(u1, u2));
        UsageScope scope = UsageScope.ofAllies(List.of(5L, 6L));
        when(queries.usageTotals(eq(scope), eq(LocalDate.of(2026, 11, 1)), any())).thenReturn(new UsageTotals(9, 6));
        when(queries.topServices(eq(scope), any(), anyInt()))
                .thenReturn(List.of(new LabelledCount("P1", "Consulta", 4)));

        DashboardDto dto = service.getMyDashboard(USER, List.of("ALLY_VIEW_OWN"));

        assertThat(dto.sections()).containsExactly(DashboardSection.ALLY);
        Map<String, DashboardKpiDto> k = kpis(dto);
        assertThat(k.get("allyMonthUsages").value()).isEqualByComparingTo("9");
        assertThat(k.get("allyMembersServed").value()).isEqualByComparingTo("6");
        assertThat(k.get("allyRating").value()).isEqualByComparingTo("4.25");
        assertThat(k.get("allyRating").count()).isEqualTo(4L);
        DashboardChartDto services = chart(dto, "allyUsagesByService");
        assertThat(services.labelKind()).isEqualTo(DashboardChartDto.LabelKind.TEXT);
        assertThat(services.points().get(0).label()).isEqualTo("Consulta");
    }

    @Test
    void beneficiaryWithAccess_seesOnlyOwnUsages_neverHolderPayments() {
        Member holder = new Member();
        holder.setId(10L);
        Beneficiary beneficiary = new Beneficiary();
        beneficiary.setId(20L);
        beneficiary.setMember(holder);
        when(beneficiaryRepository.findByUser_UuidAndActiveTrue(USER)).thenReturn(Optional.of(beneficiary));
        when(membershipRepository.findFirstByMemberIdAndActiveTrue(10L)).thenReturn(Optional.empty());
        UsageScope scope = UsageScope.ofBeneficiary(20L);
        when(queries.usageTotals(eq(scope), eq(LocalDate.of(2000, 1, 1)), any())).thenReturn(new UsageTotals(3, 1));

        DashboardDto dto = service.getMyDashboard(USER, List.of("MEMBER_VIEW_OWN"));

        assertThat(dto.sections()).containsExactly(DashboardSection.MEMBER);
        Map<String, DashboardKpiDto> k = kpis(dto);
        assertThat(k.get("membershipStatus").text()).isEqualTo("NONE");
        assertThat(k.get("memberTotalUsages").value()).isEqualByComparingTo("3");
        assertThat(k).doesNotContainKeys("beneficiaries", "nextDueDate");
        verify(queries).findRecentUsages(scope, DashboardService.ACTIVITY_LIMIT);
        verify(queries, never()).findRecentPayments(any(), any(), anyInt());
        verify(queries, never()).countUsagesByPerson(any());
    }
}

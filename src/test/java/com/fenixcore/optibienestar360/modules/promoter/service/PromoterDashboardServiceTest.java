package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterDashboardDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterMemberRow;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PromoterDashboardService} — the {@code /v1/promoter/me}
 * aggregation (v2 PDF #4). Locks the collection-health bucketing (al día /
 * vencida / sin membresía) and the not-a-promoter 404.
 */
@ExtendWith(MockitoExtension.class)
class PromoterDashboardServiceTest {

    @Mock private PromoterRepository promoterRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private CommissionRepository commissionRepository;

    private PromoterDashboardService service() {
        return new PromoterDashboardService(promoterRepository, memberRepository, commissionRepository);
    }

    @Test
    void bucketsPortfolioByMembershipStatus_andReportsPeriodCommissions() {
        UUID userUuid = UUID.randomUUID();
        Promoter promoter = promoter();
        when(promoterRepository.findActiveByUserUuid(userUuid)).thenReturn(Optional.of(promoter));
        when(memberRepository.findPromoterPortfolio(promoter.getId())).thenReturn(List.of(
                row("ACTIVE"),
                row("SUSPENDED"),
                row("EXPIRED"),
                row(null)  // enrolled, no active membership
        ));
        when(commissionRepository.sumForPromoterInPeriod(eq(promoter.getId()), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new BigDecimal("42.00"));

        PromoterDashboardDto dto = service().getMyDashboard(userUuid);

        assertThat(dto.activeAffiliates()).isEqualTo(4);
        assertThat(dto.affiliatesUpToDate()).isEqualTo(1);
        assertThat(dto.affiliatesOverdue()).isEqualTo(2);       // SUSPENDED + EXPIRED
        assertThat(dto.affiliatesWithoutMembership()).isEqualTo(1);
        assertThat(dto.periodCommissions()).isEqualByComparingTo("42.00");
        assertThat(dto.periodCurrency()).isEqualTo("USD");
        assertThat(dto.leaderboardPosition()).isNull();          // PDF #5, not built
        assertThat(dto.portfolio()).hasSize(4);
        // The queried period is the current calendar month.
        LocalDate today = LocalDate.now();
        assertThat(dto.periodStart()).isEqualTo(today.withDayOfMonth(1));
        assertThat(dto.periodEnd()).isEqualTo(today.withDayOfMonth(today.lengthOfMonth()));
    }

    @Test
    void emptyPortfolio_yieldsZeroedBuckets() {
        UUID userUuid = UUID.randomUUID();
        Promoter promoter = promoter();
        when(promoterRepository.findActiveByUserUuid(userUuid)).thenReturn(Optional.of(promoter));
        when(memberRepository.findPromoterPortfolio(promoter.getId())).thenReturn(List.of());
        when(commissionRepository.sumForPromoterInPeriod(any(), any(), any())).thenReturn(BigDecimal.ZERO);

        PromoterDashboardDto dto = service().getMyDashboard(userUuid);

        assertThat(dto.activeAffiliates()).isZero();
        assertThat(dto.affiliatesUpToDate()).isZero();
        assertThat(dto.affiliatesOverdue()).isZero();
        assertThat(dto.affiliatesWithoutMembership()).isZero();
        assertThat(dto.portfolio()).isEmpty();
    }

    @Test
    void notAPromoter_yields404_andNeverQueriesPortfolio() {
        UUID userUuid = UUID.randomUUID();
        when(promoterRepository.findActiveByUserUuid(userUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getMyDashboard(userUuid))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("me.promoter.not_found");
        verify(memberRepository, never()).findPromoterPortfolio(any());
    }

    private Promoter promoter() {
        Promoter p = new Promoter();
        p.setId(7L);
        p.setUuid(UUID.randomUUID());
        p.setDisplayName("Ana Ventas");
        p.setReferralCode("ANAV42");
        p.setActive(true);
        return p;
    }

    private PromoterMemberRow row(String status) {
        return new PromoterMemberRow(UUID.randomUUID(), "Afiliado", status,
                status == null ? null : LocalDate.of(2026, 8, 1),
                status == null ? null : new BigDecimal("5.00"));
    }
}

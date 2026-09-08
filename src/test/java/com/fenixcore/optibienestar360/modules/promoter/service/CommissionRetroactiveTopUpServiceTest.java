package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionRetroactiveTopUpRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionRetroactiveTopUpResponse;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.AppliesTo;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionRetroactiveTopUp;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionTier;
import com.fenixcore.optibienestar360.modules.promoter.entity.HierarchyOverrideTier;
import com.fenixcore.optibienestar360.modules.promoter.entity.HierarchyOverrideTier.OverrideCategory;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterHierarchyOverride;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterRank;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionRetroactiveTopUpRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionTierRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.HierarchyOverrideTierRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterHierarchyOverrideRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CommissionRetroactiveTopUpService} (V105, hub plan
 * ".ai/plans/2026-09-07-hierarchical-commissions-plan.md" §3, PR4) — the
 * gap between what already-PAID rows earned and what the settlement
 * period's final highest band would have paid on their combined basis.
 */
@ExtendWith(MockitoExtension.class)
class CommissionRetroactiveTopUpServiceTest {

    @Mock private CommissionRepository commissionRepository;
    @Mock private CommissionTierRepository commissionTierRepository;
    @Mock private PromoterHierarchyOverrideRepository overrideRepository;
    @Mock private HierarchyOverrideTierRepository hierarchyOverrideTierRepository;
    @Mock private PromoterHierarchyService hierarchyService;
    @Mock private MemberRepository memberRepository;
    @Mock private CommissionRetroactiveTopUpRepository topUpRepository;

    private CommissionRetroactiveTopUpService service() {
        return new CommissionRetroactiveTopUpService(commissionRepository, commissionTierRepository,
                overrideRepository, hierarchyOverrideTierRepository, hierarchyService, memberRepository, topUpRepository);
    }

    private static Currency usd() {
        Currency c = new Currency();
        c.setCode("USD");
        return c;
    }

    private static Promoter promoter(Long id, String name) {
        Promoter p = new Promoter();
        p.setId(id);
        p.setUuid(UUID.randomUUID());
        p.setDisplayName(name);
        p.setReferralCode(name.toUpperCase());
        return p;
    }

    private static Commission paidCommission(Promoter promoter, BigDecimal basis, BigDecimal amount, Currency currency) {
        Commission c = new Commission();
        c.setPromoter(promoter);
        c.setAppliesTo(AppliesTo.INSCRIPTION);
        c.setCalculationBasis(basis);
        c.setAmount(amount);
        c.setCurrency(currency);
        c.setStatus("PAID");
        return c;
    }

    private static CommissionTier tier(long id, int threshold, String pct) {
        CommissionTier t = new CommissionTier();
        t.setId(id);
        t.setUuid(UUID.randomUUID());
        t.setName("Tier " + threshold);
        t.setThresholdCount(threshold);
        t.setCommissionPct(new BigDecimal(pct));
        return t;
    }

    private static CommissionRetroactiveTopUpRequest request(boolean dryRun) {
        return new CommissionRetroactiveTopUpRequest(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), dryRun);
    }

    @Test
    void insertsTopUpWhenFinalBandExceedsWhatWasAlreadyPaid() {
        Promoter promoter = promoter(1L, "ase");
        Currency usd = usd();
        // Week 1 paid $250 (10 inscriptions × $25 × 10%) — but the whole month
        // reached 60 inscriptions, qualifying for the 20% band.
        Commission paid = paidCommission(promoter, new BigDecimal("250.00"), new BigDecimal("25.00"), usd);

        when(commissionRepository.findPaidForPeriod(request(false).periodStart(), request(false).periodEnd()))
                .thenReturn(List.of(paid));
        when(overrideRepository.findPaidForPeriod(any(), any())).thenReturn(List.of());
        when(memberRepository.countNewSubscribersForPromoter(1L, request(false).periodStart(), request(false).periodEnd()))
                .thenReturn(60L);
        CommissionTier baseTier = tier(1L, 0, "10.00");
        CommissionTier finalTier = tier(2L, 60, "20.00");
        when(commissionTierRepository.findActiveApplicable(null, CommissionTier.AppliesTo.INSCRIPTION, CommissionTier.AppliesTo.BOTH, null))
                .thenReturn(List.of(finalTier, baseTier));
        when(topUpRepository.findByPromoterIdAndLedgerTypeAndPeriodStartAndPeriodEnd(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(topUpRepository.save(any(CommissionRetroactiveTopUp.class))).thenAnswer(inv -> inv.getArgument(0));

        CommissionRetroactiveTopUpResponse response = service().execute(request(false));

        // target = 250 × 20% = 50.00 ; already paid = 25.00 ; retro = 25.00
        assertThat(response.totalTopUps()).isEqualTo(1);
        assertThat(response.totalRetroAmount()).isEqualByComparingTo("25.00");

        ArgumentCaptor<CommissionRetroactiveTopUp> captor = ArgumentCaptor.forClass(CommissionRetroactiveTopUp.class);
        verify(topUpRepository).save(captor.capture());
        CommissionRetroactiveTopUp saved = captor.getValue();
        assertThat(saved.getBasisAmount()).isEqualByComparingTo("250.00");
        assertThat(saved.getTargetAmount()).isEqualByComparingTo("50.00");
        assertThat(saved.getAlreadyPaidAmount()).isEqualByComparingTo("25.00");
        assertThat(saved.getRetroAmount()).isEqualByComparingTo("25.00");
        assertThat(saved.getLedgerType()).isEqualTo(CommissionRetroactiveTopUp.LedgerType.DIRECT_INSCRIPTION);
    }

    @Test
    void skipsWhenAlreadyPaidAtOrAboveTheFinalBand() {
        Promoter promoter = promoter(1L, "ase");
        Currency usd = usd();
        Commission paid = paidCommission(promoter, new BigDecimal("100.00"), new BigDecimal("30.00"), usd);

        when(commissionRepository.findPaidForPeriod(any(), any())).thenReturn(List.of(paid));
        when(overrideRepository.findPaidForPeriod(any(), any())).thenReturn(List.of());
        when(memberRepository.countNewSubscribersForPromoter(any(), any(), any())).thenReturn(5L);
        CommissionTier onlyTier = tier(1L, 0, "20.00"); // target would be 20.00, already paid 30.00 — no shortfall
        when(commissionTierRepository.findActiveApplicable(null, CommissionTier.AppliesTo.INSCRIPTION, CommissionTier.AppliesTo.BOTH, null))
                .thenReturn(List.of(onlyTier));

        CommissionRetroactiveTopUpResponse response = service().execute(request(false));

        assertThat(response.totalTopUps()).isZero();
        verify(topUpRepository, never()).save(any());
    }

    @Test
    void dryRunComputesWithoutSaving() {
        Promoter promoter = promoter(1L, "ase");
        Commission paid = paidCommission(promoter, new BigDecimal("100.00"), new BigDecimal("10.00"), usd());

        when(commissionRepository.findPaidForPeriod(any(), any())).thenReturn(List.of(paid));
        when(overrideRepository.findPaidForPeriod(any(), any())).thenReturn(List.of());
        when(memberRepository.countNewSubscribersForPromoter(any(), any(), any())).thenReturn(50L);
        CommissionTier finalTier = tier(1L, 0, "25.00");
        when(commissionTierRepository.findActiveApplicable(null, CommissionTier.AppliesTo.INSCRIPTION, CommissionTier.AppliesTo.BOTH, null))
                .thenReturn(List.of(finalTier));

        CommissionRetroactiveTopUpResponse response = service().execute(request(true));

        assertThat(response.dryRun()).isTrue();
        assertThat(response.totalRetroAmount()).isEqualByComparingTo("15.00"); // 25.00 - 10.00
        verify(topUpRepository, never()).save(any());
    }

    @Test
    void computesHierarchyOverrideTopUpUsingTeamVolume() {
        PromoterRank supervisorRank = new PromoterRank();
        supervisorRank.setId(2L);
        supervisorRank.setUuid(UUID.randomUUID());
        supervisorRank.setCode("SUPERVISOR");
        Promoter sup = promoter(2L, "sup");
        sup.setRank(supervisorRank);

        PromoterHierarchyOverride paidOverride = new PromoterHierarchyOverride();
        paidOverride.setPromoter(sup);
        paidOverride.setCategory(OverrideCategory.INSCRIPTION);
        paidOverride.setBasisAmount(new BigDecimal("100.00"));
        paidOverride.setAmount(new BigDecimal("10.00"));
        paidOverride.setCurrency(usd());

        when(commissionRepository.findPaidForPeriod(any(), any())).thenReturn(List.of());
        when(overrideRepository.findPaidForPeriod(any(), any())).thenReturn(List.of(paidOverride));
        when(hierarchyService.resolveTeamMemberIds(eq(2L), any())).thenReturn(Set.of(1L));
        when(memberRepository.countNewSubscribersForPromoters(Set.of(1L),
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))).thenReturn(150L);
        HierarchyOverrideTier finalTier = new HierarchyOverrideTier();
        finalTier.setId(9L);
        finalTier.setUuid(UUID.randomUUID());
        finalTier.setName("Premium");
        finalTier.setThresholdCount(100);
        finalTier.setOverridePct(new BigDecimal("20.00"));
        when(hierarchyOverrideTierRepository.findActiveApplicable(2L, OverrideCategory.INSCRIPTION))
                .thenReturn(List.of(finalTier));
        when(topUpRepository.findByPromoterIdAndLedgerTypeAndPeriodStartAndPeriodEnd(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(topUpRepository.save(any(CommissionRetroactiveTopUp.class))).thenAnswer(inv -> inv.getArgument(0));

        CommissionRetroactiveTopUpResponse response = service().execute(request(false));

        assertThat(response.totalTopUps()).isEqualTo(1);
        assertThat(response.totalRetroAmount()).isEqualByComparingTo("10.00"); // 20.00 - 10.00
        assertThat(response.topUps().get(0).ledgerType()).isEqualTo("HIERARCHY_OVERRIDE_INSCRIPTION");
    }
}

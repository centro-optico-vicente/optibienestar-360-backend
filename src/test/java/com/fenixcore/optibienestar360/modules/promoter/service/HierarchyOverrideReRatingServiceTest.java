package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.HierarchyOverrideReRatingRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.HierarchyOverrideReRatingResponse;
import com.fenixcore.optibienestar360.modules.promoter.dto.HierarchyOverrideReRatingResponse.BeneficiaryReRatingOutcome;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission;
import com.fenixcore.optibienestar360.modules.promoter.entity.HierarchyOverrideTier;
import com.fenixcore.optibienestar360.modules.promoter.entity.HierarchyOverrideTier.OverrideCategory;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterHierarchyOverride;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterRank;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.HierarchyOverrideTierRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterHierarchyOverrideRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link HierarchyOverrideReRatingService} (V102, hub plan
 * ".ai/plans/2026-09-07-hierarchical-commissions-plan.md" §2, PR3) — both
 * passes: basis resync (a stale source amount propagates, one level at a
 * time) and band re-rate (team volume crossing a threshold mid-period bumps
 * every PENDING row at once, never a progressive blend).
 */
@ExtendWith(MockitoExtension.class)
class HierarchyOverrideReRatingServiceTest {

    @Mock private PromoterHierarchyOverrideRepository overrideRepository;
    @Mock private HierarchyOverrideTierRepository tierRepository;
    @Mock private PromoterHierarchyService hierarchyService;
    @Mock private MemberRepository memberRepository;
    @Mock private CommissionRepository commissionRepository;

    private HierarchyOverrideReRatingService service() {
        return new HierarchyOverrideReRatingService(overrideRepository, tierRepository, hierarchyService,
                memberRepository, commissionRepository);
    }

    private static PromoterRank rank(String code, int level) {
        PromoterRank r = new PromoterRank();
        r.setId((long) level);
        r.setUuid(UUID.randomUUID());
        r.setCode(code);
        r.setName(code);
        r.setHierarchyLevel(level);
        return r;
    }

    private static Promoter promoter(Long id, String displayName, PromoterRank rank) {
        Promoter p = new Promoter();
        p.setId(id);
        p.setUuid(UUID.randomUUID());
        p.setDisplayName(displayName);
        p.setReferralCode(displayName.toUpperCase());
        p.setRank(rank);
        return p;
    }

    private static HierarchyOverrideTier tier(long id, PromoterRank rank, int threshold, String pct) {
        HierarchyOverrideTier t = new HierarchyOverrideTier();
        t.setId(id);
        t.setUuid(UUID.randomUUID());
        t.setName(rank.getCode() + " " + threshold);
        t.setRank(rank);
        t.setCategory(OverrideCategory.INSCRIPTION);
        t.setThresholdCount(threshold);
        t.setOverridePct(new BigDecimal(pct));
        t.setAccrualPeriodStrategy(Commission.PeriodStrategy.MONTHLY);
        return t;
    }

    private static PromoterHierarchyOverride override(Promoter beneficiary, HierarchyOverrideTier tier,
                                                       BigDecimal basisAmount, BigDecimal amount) {
        PromoterHierarchyOverride o = new PromoterHierarchyOverride();
        o.setId(1L);
        o.setUuid(UUID.randomUUID());
        o.setPromoter(beneficiary);
        o.setCategory(OverrideCategory.INSCRIPTION);
        o.setTier(tier);
        o.setBasisAmount(basisAmount);
        o.setAmount(amount);
        o.setPeriodStart(LocalDate.of(2026, 9, 1));
        o.setPeriodEnd(LocalDate.of(2026, 9, 30));
        return o;
    }

    private static HierarchyOverrideReRatingRequest request(boolean dryRun) {
        return new HierarchyOverrideReRatingRequest(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), dryRun);
    }

    @Test
    void resyncsBasisWhenSourceCommissionWasRerated() {
        PromoterRank supervisorRank = rank("SUPERVISOR", 2);
        Promoter sup = promoter(2L, "sup", supervisorRank);
        HierarchyOverrideTier baseTier = tier(1L, supervisorRank, 0, "10.00");

        Commission commission = new Commission();
        commission.setAmount(new BigDecimal("150.00")); // re-rated after the cascade first ran

        PromoterHierarchyOverride o = override(sup, baseTier, new BigDecimal("100.00"), new BigDecimal("10.00"));
        o.setSourceCommission(commission);

        when(overrideRepository.findPendingForPeriod(request(false).periodStart(), request(false).periodEnd()))
                .thenReturn(List.of(o));
        lenient().when(hierarchyService.resolveTeamMemberIds(any(), any())).thenReturn(Set.of());
        lenient().when(tierRepository.findActiveApplicable(supervisorRank.getId(), OverrideCategory.INSCRIPTION))
                .thenReturn(List.of(baseTier));

        HierarchyOverrideReRatingResponse response = service().execute(request(false));

        assertThat(o.getBasisAmount()).isEqualByComparingTo("150.00");
        assertThat(o.getAmount()).isEqualByComparingTo("15.00"); // 10% of the RESYNCED basis
        assertThat(response.totalDeltaAmount()).isEqualByComparingTo("5.00");
    }

    @Test
    void reRatesToHigherBandWhenTeamVolumeCrossesThreshold() {
        PromoterRank supervisorRank = rank("SUPERVISOR", 2);
        Promoter sup = promoter(2L, "sup", supervisorRank);
        HierarchyOverrideTier baseTier = tier(1L, supervisorRank, 0, "10.00");
        HierarchyOverrideTier premiumTier = tier(2L, supervisorRank, 100, "20.00");

        PromoterHierarchyOverride o = override(sup, baseTier, new BigDecimal("100.00"), new BigDecimal("10.00"));
        Commission commission = new Commission();
        commission.setAmount(new BigDecimal("100.00")); // unchanged — no resync needed
        o.setSourceCommission(commission);

        when(overrideRepository.findPendingForPeriod(request(false).periodStart(), request(false).periodEnd()))
                .thenReturn(List.of(o));
        when(hierarchyService.resolveTeamMemberIds(org.mockito.ArgumentMatchers.eq(sup.getId()), any())).thenReturn(Set.of(1L));
        when(memberRepository.countNewSubscribersForPromoters(Set.of(1L),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))).thenReturn(150L);
        // Highest-threshold-first, as the repository's own ordering guarantees.
        when(tierRepository.findActiveApplicable(supervisorRank.getId(), OverrideCategory.INSCRIPTION))
                .thenReturn(List.of(premiumTier, baseTier));

        HierarchyOverrideReRatingResponse response = service().execute(request(false));

        assertThat(o.getTier()).isSameAs(premiumTier);
        assertThat(o.getAmount()).isEqualByComparingTo("20.00");
        assertThat(response.totalDeltaAmount()).isEqualByComparingTo("10.00");
        BeneficiaryReRatingOutcome outcome = response.perBeneficiary().get(0);
        assertThat(outcome.overridesChanged()).isEqualTo(1);
        assertThat(outcome.targetTierUuid()).isEqualTo(premiumTier.getUuid());
    }

    @Test
    void dryRunComputesDeltasWithoutMutating() {
        PromoterRank supervisorRank = rank("SUPERVISOR", 2);
        Promoter sup = promoter(2L, "sup", supervisorRank);
        HierarchyOverrideTier baseTier = tier(1L, supervisorRank, 0, "10.00");

        Commission commission = new Commission();
        commission.setAmount(new BigDecimal("150.00"));
        PromoterHierarchyOverride o = override(sup, baseTier, new BigDecimal("100.00"), new BigDecimal("10.00"));
        o.setSourceCommission(commission);

        when(overrideRepository.findPendingForPeriod(request(true).periodStart(), request(true).periodEnd()))
                .thenReturn(List.of(o));
        lenient().when(hierarchyService.resolveTeamMemberIds(any(), any())).thenReturn(Set.of());
        lenient().when(tierRepository.findActiveApplicable(supervisorRank.getId(), OverrideCategory.INSCRIPTION))
                .thenReturn(List.of(baseTier));

        HierarchyOverrideReRatingResponse response = service().execute(request(true));

        assertThat(response.dryRun()).isTrue();
        assertThat(response.totalDeltaAmount()).isEqualByComparingTo("5.00");
        // Nothing actually mutated — still the stale, pre-resync values.
        assertThat(o.getBasisAmount()).isEqualByComparingTo("100.00");
        assertThat(o.getAmount()).isEqualByComparingTo("10.00");
    }
}

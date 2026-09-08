package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link HierarchyOverrideService} (V102, hub plan
 * ".ai/plans/2026-09-07-hierarchical-commissions-plan.md" §2) — the strict
 * one-level-at-a-time cascade: a level-2 override is funded by the source
 * {@link Commission}, a level-3+ override is funded by the override the
 * level right below just earned, and the chain stops the moment {@code
 * PromoterHierarchyService.resolveSupervisorAt} returns {@code null}.
 */
@ExtendWith(MockitoExtension.class)
class HierarchyOverrideServiceTest {

    @Mock private PromoterHierarchyService hierarchyService;
    @Mock private HierarchyOverrideTierRepository tierRepository;
    @Mock private PromoterHierarchyOverrideRepository overrideRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private CommissionRepository commissionRepository;

    private HierarchyOverrideService service() {
        return new HierarchyOverrideService(hierarchyService, tierRepository, overrideRepository,
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

    private static HierarchyOverrideTier tier(PromoterRank rank, OverrideCategory category, int threshold, String pct) {
        HierarchyOverrideTier t = new HierarchyOverrideTier();
        t.setUuid(UUID.randomUUID());
        t.setName(rank.getCode() + " " + category);
        t.setRank(rank);
        t.setCategory(category);
        t.setThresholdCount(threshold);
        t.setOverridePct(new BigDecimal(pct));
        t.setPeriodStrategy(Commission.PeriodStrategy.MONTHLY);
        return t;
    }

    @Test
    void cascadesTwoLevelsFundingEachOverrideFromTheOneBelow() {
        PromoterRank promotorRank = rank("PROMOTOR", 1);
        PromoterRank supervisorRank = rank("SUPERVISOR", 2);
        PromoterRank coordRank = rank("COORDINADOR", 3);

        Promoter ase = promoter(1L, "ase", promotorRank);
        Promoter sup = promoter(2L, "sup", supervisorRank);
        Promoter coord = promoter(3L, "coord", coordRank);

        Currency usd = new Currency();
        usd.setCode("USD");

        Commission commission = new Commission();
        commission.setPromoter(ase);
        commission.setAmount(new BigDecimal("100.00"));
        commission.setAppliesTo(Commission.AppliesTo.INSCRIPTION);
        commission.setCurrency(usd);
        Instant asOf = Instant.now();
        commission.setEarnedAt(asOf);

        when(hierarchyService.resolveSupervisorAt(1L, asOf)).thenReturn(sup);
        when(hierarchyService.resolveSupervisorAt(2L, asOf)).thenReturn(coord);
        when(hierarchyService.resolveSupervisorAt(3L, asOf)).thenReturn(null);
        when(hierarchyService.resolveTeamMemberIds(eq(2L), any())).thenReturn(Set.of(1L));
        when(hierarchyService.resolveTeamMemberIds(eq(3L), any())).thenReturn(Set.of(1L, 2L));

        HierarchyOverrideTier supTier = tier(supervisorRank, OverrideCategory.INSCRIPTION, 0, "10.00");
        HierarchyOverrideTier coordTier = tier(coordRank, OverrideCategory.INSCRIPTION, 0, "20.00");
        when(tierRepository.findActiveApplicable(supervisorRank.getId(), OverrideCategory.INSCRIPTION))
                .thenReturn(List.of(supTier));
        when(tierRepository.findActiveApplicable(coordRank.getId(), OverrideCategory.INSCRIPTION))
                .thenReturn(List.of(coordTier));

        when(overrideRepository.save(any(PromoterHierarchyOverride.class))).thenAnswer(inv -> inv.getArgument(0));

        service().cascadeFrom(commission);

        ArgumentCaptor<PromoterHierarchyOverride> captor = ArgumentCaptor.forClass(PromoterHierarchyOverride.class);
        verify(overrideRepository, times(2)).save(captor.capture());
        List<PromoterHierarchyOverride> saved = captor.getAllValues();

        PromoterHierarchyOverride supOverride = saved.get(0);
        assertThat(supOverride.getPromoter()).isSameAs(sup);
        assertThat(supOverride.getBasisAmount()).isEqualByComparingTo("100.00");
        assertThat(supOverride.getAmount()).isEqualByComparingTo("10.00");
        assertThat(supOverride.getSourceCommission()).isSameAs(commission);
        assertThat(supOverride.getSourceOverride()).isNull();

        PromoterHierarchyOverride coordOverride = saved.get(1);
        assertThat(coordOverride.getPromoter()).isSameAs(coord);
        // Funded by the Supervisor's OWN override amount ($10), never the original $100.
        assertThat(coordOverride.getBasisAmount()).isEqualByComparingTo("10.00");
        assertThat(coordOverride.getAmount()).isEqualByComparingTo("2.00");
        assertThat(coordOverride.getSourceCommission()).isNull();
        assertThat(coordOverride.getSourceOverride()).isSameAs(supOverride);
    }

    @Test
    void stopsWhenEarnerHasNoSupervisor() {
        PromoterRank promotorRank = rank("PROMOTOR", 1);
        Promoter ase = promoter(1L, "ase", promotorRank);

        Commission commission = new Commission();
        commission.setPromoter(ase);
        commission.setAmount(new BigDecimal("50.00"));
        commission.setAppliesTo(Commission.AppliesTo.INSCRIPTION);
        Instant asOf = Instant.now();
        commission.setEarnedAt(asOf);

        when(hierarchyService.resolveSupervisorAt(1L, asOf)).thenReturn(null);

        service().cascadeFrom(commission);

        verify(overrideRepository, never()).save(any());
    }

    @Test
    void skipsWhenNoTierConfiguredForSupervisorRank() {
        PromoterRank promotorRank = rank("PROMOTOR", 1);
        PromoterRank supervisorRank = rank("SUPERVISOR", 2);
        Promoter ase = promoter(1L, "ase", promotorRank);
        Promoter sup = promoter(2L, "sup", supervisorRank);

        Commission commission = new Commission();
        commission.setPromoter(ase);
        commission.setAmount(new BigDecimal("50.00"));
        commission.setAppliesTo(Commission.AppliesTo.MONTHLY);
        Instant asOf = Instant.now();
        commission.setEarnedAt(asOf);

        when(hierarchyService.resolveSupervisorAt(1L, asOf)).thenReturn(sup);
        when(tierRepository.findActiveApplicable(supervisorRank.getId(), OverrideCategory.COLLECTION))
                .thenReturn(List.of());

        service().cascadeFrom(commission);

        verify(overrideRepository, never()).save(any());
    }
}

package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.modules.campaign.repository.CampaignRepository;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.HierarchyOverrideTierCreateRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.HierarchyOverrideTierDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.HierarchyOverrideTierUpdateRequest;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;
import com.fenixcore.optibienestar360.modules.promoter.entity.HierarchyOverrideTier;
import com.fenixcore.optibienestar360.modules.promoter.entity.HierarchyOverrideTier.OverrideCategory;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterRank;
import com.fenixcore.optibienestar360.modules.promoter.repository.HierarchyOverrideTierRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterHierarchyOverrideRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRankRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HierarchyOverrideTiersServiceTest {

    @Mock private HierarchyOverrideTierRepository repository;
    @Mock private PromoterRankRepository rankRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private PromoterHierarchyOverrideRepository overrideRepository;
    @Mock private DefaultSortResolver defaultSortResolver;

    private HierarchyOverrideTiersService sut() {
        return new HierarchyOverrideTiersService(repository, rankRepository, currencyRepository,
                campaignRepository, overrideRepository, defaultSortResolver);
    }

    private static PromoterRank rank() {
        PromoterRank r = new PromoterRank();
        r.setId(1L);
        r.setUuid(UUID.randomUUID());
        r.setCode("SUPERVISOR");
        r.setName("Supervisor");
        r.setHierarchyLevel(2);
        return r;
    }

    private static HierarchyOverrideTierCreateRequest request(BigDecimal pct, BigDecimal flat, UUID currencyUuid) {
        return new HierarchyOverrideTierCreateRequest(
                "Override apertura", null, UUID.randomUUID(), OverrideCategory.INSCRIPTION, 0, pct, flat, currencyUuid,
                PeriodStrategy.MONTHLY, null, null, null);
    }

    @Test
    void create_rejectsWhenBothPctAndFlat() {
        assertThatThrownBy(() -> sut().create(request(new BigDecimal("10"), new BigDecimal("5"), UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("hierarchy_override_tier.pct_xor_flat");
        verify(repository, never()).save(any());
    }

    @Test
    void create_rejectsWhenNeitherPctNorFlat() {
        assertThatThrownBy(() -> sut().create(request(null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("hierarchy_override_tier.pct_xor_flat");
    }

    @Test
    void create_rejectsFlatAmountWithoutCurrency() {
        HierarchyOverrideTierCreateRequest req = request(null, new BigDecimal("5.00"), null);

        assertThatThrownBy(() -> sut().create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("hierarchy_override_tier.flat_amount.currency_required");
    }

    @Test
    void create_persistsValidPct() {
        HierarchyOverrideTierCreateRequest req = request(new BigDecimal("10.00"), null, null);
        PromoterRank rank = rank();
        when(rankRepository.findByUuid(req.rankUuid())).thenReturn(Optional.of(rank));
        when(repository.save(any(HierarchyOverrideTier.class))).thenAnswer(inv -> inv.getArgument(0));

        HierarchyOverrideTierDto result = sut().create(req);

        assertThat(result.overridePct()).isEqualByComparingTo("10.00");
        assertThat(result.flatAmount()).isNull();
    }

    @Test
    void create_persistsValidFlatWithCurrency() {
        UUID currencyUuid = UUID.randomUUID();
        HierarchyOverrideTierCreateRequest req = request(null, new BigDecimal("5.00"), currencyUuid);
        PromoterRank rank = rank();
        Currency usd = new Currency();
        usd.setUuid(currencyUuid);
        usd.setCode("USD");
        when(rankRepository.findByUuid(req.rankUuid())).thenReturn(Optional.of(rank));
        when(currencyRepository.findByUuid(currencyUuid)).thenReturn(Optional.of(usd));
        when(repository.save(any(HierarchyOverrideTier.class))).thenAnswer(inv -> inv.getArgument(0));

        HierarchyOverrideTierDto result = sut().create(req);

        assertThat(result.flatAmount()).isEqualByComparingTo("5.00");
        assertThat(result.flatAmountCurrency_Code()).isEqualTo("USD");
    }

    @Test
    void update_switchingToFlatClearsExistingPctAndRequiresCurrency() {
        UUID uuid = UUID.randomUUID();
        HierarchyOverrideTier existing = new HierarchyOverrideTier();
        existing.setUuid(uuid);
        existing.setRank(rank());
        existing.setCategory(OverrideCategory.INSCRIPTION);
        existing.setOverridePct(new BigDecimal("10.00"));
        when(repository.findByUuid(uuid)).thenReturn(Optional.of(existing));

        HierarchyOverrideTierUpdateRequest req = new HierarchyOverrideTierUpdateRequest(
                null, null, null, null, null, null, new BigDecimal("5.00"), null, null, null, null, null, null);

        assertThatThrownBy(() -> sut().update(uuid, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("hierarchy_override_tier.flat_amount.currency_required");
    }

    @Test
    void delete_softDeletesByDefault() {
        UUID uuid = UUID.randomUUID();
        HierarchyOverrideTier existing = new HierarchyOverrideTier();
        existing.setUuid(uuid);
        existing.setId(1L);
        existing.setActive(true);
        when(repository.findByUuid(uuid)).thenReturn(Optional.of(existing));
        when(overrideRepository.countByTierId(1L)).thenReturn(0L);

        sut().delete(uuid, false);

        assertThat(existing.isActive()).isFalse();
        verify(repository, never()).delete(any(HierarchyOverrideTier.class));
    }

    @Test
    void delete_hardDeletesWhenPhysicalAndUnreferenced() {
        UUID uuid = UUID.randomUUID();
        HierarchyOverrideTier existing = new HierarchyOverrideTier();
        existing.setUuid(uuid);
        existing.setId(1L);
        when(repository.findByUuid(uuid)).thenReturn(Optional.of(existing));
        when(overrideRepository.countByTierId(1L)).thenReturn(0L);

        sut().delete(uuid, true);

        verify(repository).delete(existing);
    }
}

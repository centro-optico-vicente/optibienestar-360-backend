package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.catalog.repository.PromoterTypeRepository;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan.PlanType;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionTierCreateRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionTierUpdateRequest;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionTier;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionTier.AppliesTo;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionTierRepository;
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
class CommissionTiersServiceTest {

    @Mock private CommissionTierRepository repository;
    @Mock private PromoterTypeRepository promoterTypeRepository;

    private CommissionTiersService sut() {
        return new CommissionTiersService(repository, promoterTypeRepository);
    }

    @Test
    void create_rejects_whenBothPctAndFlat() {
        CommissionTierCreateRequest req = new CommissionTierCreateRequest(
                "bad", PlanType.INDIVIDUAL, null, 0, new BigDecimal("20"), new BigDecimal("5"),
                PeriodStrategy.MONTHLY, AppliesTo.BOTH);

        assertThatThrownBy(() -> sut().create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("commission_tier.pct_xor_flat");
        verify(repository, never()).save(any());
    }

    @Test
    void create_rejects_whenNeitherPctNorFlat() {
        CommissionTierCreateRequest req = new CommissionTierCreateRequest(
                "bad", null, null, 0, null, null, PeriodStrategy.MONTHLY, AppliesTo.BOTH);

        assertThatThrownBy(() -> sut().create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("commission_tier.pct_xor_flat");
    }

    @Test
    void create_persists_validPct() {
        CommissionTierCreateRequest req = new CommissionTierCreateRequest(
                "Gold 25%", PlanType.FAMILIAR, null, 10, new BigDecimal("25"), null,
                PeriodStrategy.MONTHLY, AppliesTo.MONTHLY);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        var dto = sut().create(req);

        assertThat(dto.commissionPct()).isEqualByComparingTo("25");
        assertThat(dto.flatAmount()).isNull();
        assertThat(dto.thresholdCount()).isEqualTo(10);
    }

    @Test
    void update_switchToFlat_clearsPct() {
        CommissionTier tier = new CommissionTier();
        tier.setUuid(UUID.randomUUID());
        tier.setName("t");
        tier.setCommissionPct(new BigDecimal("20"));
        tier.setPeriodStrategy(PeriodStrategy.MONTHLY);
        tier.setAppliesTo(AppliesTo.BOTH);
        when(repository.findByUuid(tier.getUuid())).thenReturn(Optional.of(tier));

        CommissionTierUpdateRequest req = new CommissionTierUpdateRequest(
                null, null, null, null, null, new BigDecimal("7"), null, null, null);

        var dto = sut().update(tier.getUuid(), req);

        assertThat(dto.flatAmount()).isEqualByComparingTo("7");
        assertThat(dto.commissionPct()).isNull();
    }

    @Test
    void update_rejects_whenBothProvided() {
        CommissionTier tier = new CommissionTier();
        tier.setUuid(UUID.randomUUID());
        tier.setCommissionPct(new BigDecimal("20"));
        tier.setPeriodStrategy(PeriodStrategy.MONTHLY);
        tier.setAppliesTo(AppliesTo.BOTH);
        when(repository.findByUuid(tier.getUuid())).thenReturn(Optional.of(tier));

        CommissionTierUpdateRequest req = new CommissionTierUpdateRequest(
                null, null, null, null, new BigDecimal("20"), new BigDecimal("5"), null, null, null);

        assertThatThrownBy(() -> sut().update(tier.getUuid(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("commission_tier.pct_xor_flat");
    }
}

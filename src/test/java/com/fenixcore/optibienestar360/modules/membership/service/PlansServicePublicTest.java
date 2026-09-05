package com.fenixcore.optibienestar360.modules.membership.service;

import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.modules.membership.dto.PublicPlanDto;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan.PlanType;
import com.fenixcore.optibienestar360.modules.membership.mapper.PlanMapperImpl;
import com.fenixcore.optibienestar360.modules.corporate.repository.CorporateContractRepository;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.membership.repository.PlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the <b>anonymous</b> read path of {@link PlansService}
 * ({@code publicList} / {@code publicGetByUuid}), the pricing surface behind
 * {@code GET /v1/public/plans}. Uses the real {@code PlanMapperImpl} — same
 * approach as {@code MyAlliesServiceTest} — so the sanitized
 * {@link PublicPlanDto} projection is exercised end-to-end rather than stubbed.
 *
 * <p>The invariant these lock: an anonymous caller must never see a plan that
 * is not both <b>published and active</b>. For detail-by-UUID the gate lives in
 * the service ({@code .filter(Plan::isActive).filter(Plan::isPublished)}), not
 * only in the list SQL spec, so probing an unpublished plan's UUID must 404 the
 * same as a non-existent one.</p>
 */
@ExtendWith(MockitoExtension.class)
class PlansServicePublicTest {

    @Mock private PlanRepository repository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private CorporateContractRepository corporateContractRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private com.fenixcore.optibienestar360.modules.currency.service.ConversionEnricher conversionEnricher;
    @Mock private DefaultSortResolver defaultSortResolver;

    private PlansService service;

    @BeforeEach
    void setup() {
        service = new PlansService(repository, new PlanMapperImpl(), membershipRepository, corporateContractRepository,
                currencyRepository, conversionEnricher, defaultSortResolver);
    }

    // ─── publicGetByUuid ─────────────────────────────────────────────────────

    @Test
    void publicGetByUuid_returnsSanitizedDto_forPublishedActivePlan() {
        Plan plan = familiarPlan(true, true);
        when(repository.findByUuid(plan.getUuid())).thenReturn(Optional.of(plan));

        PublicPlanDto dto = service.publicGetByUuid(plan.getUuid());

        assertThat(dto.uuid()).isEqualTo(plan.getUuid());
        assertThat(dto.code()).isEqualTo("FAMILIAR");
        assertThat(dto.name()).isEqualTo("Plan Familiar");
        assertThat(dto.type()).isEqualTo(PlanType.FAMILIAR);
        assertThat(dto.inscriptionFee()).isEqualByComparingTo("20.00");
        assertThat(dto.monthlyFee()).isEqualByComparingTo("5.00");
        assertThat(dto.includedBeneficiaries()).isEqualTo(3);
        assertThat(dto.maxBeneficiaries()).isEqualTo(5);
        assertThat(dto.extraBeneficiaryInscriptionFee()).isEqualByComparingTo("5.00");
        assertThat(dto.gracePeriodDays()).isEqualTo(7);
    }

    @Test
    void publicGetByUuid_404s_whenPlanIsUnpublished() {
        // Published flag off → invisible to anonymous callers, even probing the UUID directly.
        Plan plan = familiarPlan(true, false);
        when(repository.findByUuid(plan.getUuid())).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.publicGetByUuid(plan.getUuid()))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("plan.not_found");
    }

    @Test
    void publicGetByUuid_404s_whenPlanIsInactive() {
        // Soft-deleted / deactivated plan is never publicly visible.
        Plan plan = familiarPlan(false, true);
        when(repository.findByUuid(plan.getUuid())).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.publicGetByUuid(plan.getUuid()))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("plan.not_found");
    }

    @Test
    void publicGetByUuid_404s_whenPlanDoesNotExist() {
        UUID missing = UUID.randomUUID();
        when(repository.findByUuid(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publicGetByUuid(missing))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("plan.not_found");
    }

    // ─── publicList ──────────────────────────────────────────────────────────

    @Test
    void publicList_mapsRowsThroughTheSanitizedProjection() {
        Plan plan = familiarPlan(true, true);
        Pageable pageable = PageRequest.of(0, 50);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(plan), pageable, 1));

        Page<PublicPlanDto> page = service.publicList(pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        PublicPlanDto dto = page.getContent().getFirst();
        assertThat(dto.uuid()).isEqualTo(plan.getUuid());
        assertThat(dto.code()).isEqualTo("FAMILIAR");
        assertThat(dto.monthlyFee()).isEqualByComparingTo("5.00");
    }

    // ─── Fixtures ────────────────────────────────────────────────────────────

    private Plan familiarPlan(boolean active, boolean published) {
        Plan p = new Plan();
        p.setId(10L);
        p.setUuid(UUID.randomUUID());
        p.setCode("FAMILIAR");
        p.setName("Plan Familiar");
        p.setDescription("Cobertura para el titular y su grupo familiar");
        p.setType(PlanType.FAMILIAR);
        p.setInscriptionFee(new BigDecimal("20.00"));
        p.setMonthlyFee(new BigDecimal("5.00"));
        p.setIncludedBeneficiaries(3);
        p.setMaxBeneficiaries(5);
        p.setExtraBeneficiaryInscriptionFee(new BigDecimal("5.00"));
        p.setGracePeriodDays(7);
        p.setActive(active);
        p.setPublished(published);
        return p;
    }
}

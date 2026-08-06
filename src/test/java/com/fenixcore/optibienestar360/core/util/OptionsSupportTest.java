package com.fenixcore.optibienestar360.core.util;

import com.fenixcore.optibienestar360.core.dto.OptionDto;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan.PlanType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OptionsSupport} — the shared builder behind every
 * {@code /options} endpoint. Uses {@link Plan} as a concrete stand-in entity
 * since the helper is generic over any {@code JpaSpecificationExecutor}.
 */
@ExtendWith(MockitoExtension.class)
class OptionsSupportTest {

    @Mock
    private JpaSpecificationExecutor<Plan> specExecutor;

    private static Plan plan(UUID uuid, String code, String name, boolean active) {
        Plan p = new Plan();
        p.setUuid(uuid);
        p.setCode(code);
        p.setName(name);
        p.setType(PlanType.INDIVIDUAL);
        p.setInscriptionFee(BigDecimal.ZERO);
        p.setMonthlyFee(BigDecimal.ZERO);
        p.setActive(active);
        return p;
    }

    private List<OptionDto> build(Specification<Plan> spec, List<UUID> currentValues, int limit,
                                   java.util.function.Function<UUID, Optional<Plan>> findByUuid) {
        return OptionsSupport.build(specExecutor, findByUuid, spec, currentValues, limit,
                Plan::getUuid, Plan::getCode, Plan::getName, Plan::isActive);
    }

    @Test
    void resultsAreMappedToOptionDtoInOrder() {
        Plan familiar = plan(UUID.randomUUID(), "FAMILIAR", "Plan Familiar", true);
        when(specExecutor.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(familiar)));

        List<OptionDto> options = build((root, query, cb) -> cb.conjunction(), null, 50, uuid -> Optional.empty());

        assertThat(options).hasSize(1);
        OptionDto dto = options.getFirst();
        assertThat(dto.uuid()).isEqualTo(familiar.getUuid());
        assertThat(dto.code()).isEqualTo("FAMILIAR");
        assertThat(dto.label()).isEqualTo("Plan Familiar");
        assertThat(dto.active()).isTrue();
    }

    @Test
    void limitIsCappedAt200EvenWhenCallerAsksForMore() {
        when(specExecutor.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        build((root, query, cb) -> cb.conjunction(), null, 5000, uuid -> Optional.empty());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(specExecutor).findAll(any(Specification.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(200);
    }

    @Test
    void limitBelowOneIsRaisedToOne() {
        when(specExecutor.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        build((root, query, cb) -> cb.conjunction(), null, 0, uuid -> Optional.empty());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(specExecutor).findAll(any(Specification.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(1);
    }

    @Test
    void currentValues_inactiveAndOutsideBaseFilter_stillAppearsWithActiveFalse() {
        // Base query (active=true) finds nothing — simulating an inactive plan
        // already assigned to a membership (currentValues) but filtered out of
        // the "active only" default result set.
        when(specExecutor.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        UUID inactiveUuid = UUID.randomUUID();
        Plan inactivePlan = plan(inactiveUuid, "OLD", "Discontinued Plan", false);

        List<OptionDto> options = build((root, query, cb) -> cb.conjunction(),
                List.of(inactiveUuid), 50, uuid -> uuid.equals(inactiveUuid) ? Optional.of(inactivePlan) : Optional.empty());

        assertThat(options).hasSize(1);
        assertThat(options.getFirst().uuid()).isEqualTo(inactiveUuid);
        assertThat(options.getFirst().active()).isFalse();
    }

    @Test
    void currentValues_alreadyInBaseResults_isNotDuplicated() {
        UUID uuid = UUID.randomUUID();
        Plan p = plan(uuid, "FAMILIAR", "Plan Familiar", true);
        when(specExecutor.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p)));

        List<OptionDto> options = build((root, query, cb) -> cb.conjunction(),
                List.of(uuid), 50, u -> Optional.of(p));

        assertThat(options).hasSize(1);
    }

    @Test
    void currentValues_unknownUuid_isSilentlySkipped() {
        when(specExecutor.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        List<OptionDto> options = build((root, query, cb) -> cb.conjunction(),
                List.of(UUID.randomUUID()), 50, uuid -> Optional.empty());

        assertThat(options).isEmpty();
    }

    @Test
    void nullCurrentValues_isTreatedAsEmpty() {
        when(specExecutor.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        List<OptionDto> options = build((root, query, cb) -> cb.conjunction(), null, 50, uuid -> Optional.empty());

        assertThat(options).isEmpty();
    }
}

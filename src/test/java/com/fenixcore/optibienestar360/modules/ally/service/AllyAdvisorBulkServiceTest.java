package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.modules.ally.dto.AllyBulkAssignResultDto;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link AllyAdvisorBulkService} (ADR LOC-0016): skip of allies already on the target and the 422/404 guards. */
@ExtendWith(MockitoExtension.class)
class AllyAdvisorBulkServiceTest {

    @Mock private AlliesService alliesService;
    @Mock private AllyRepository allyRepository;
    @Mock private PromoterRepository promoterRepository;

    @InjectMocks
    private AllyAdvisorBulkService service;

    @Test
    void movesEachAllyThroughAssignAdvisor_andSkipsTheOnesAlreadyOnTheTarget() {
        Promoter target = promoter(true, false);
        Ally moved = ally(null);
        Ally already = ally(target);
        when(promoterRepository.findByUuid(target.getUuid())).thenReturn(Optional.of(target));
        when(allyRepository.findByUuid(moved.getUuid())).thenReturn(Optional.of(moved));
        when(allyRepository.findByUuid(already.getUuid())).thenReturn(Optional.of(already));

        AllyBulkAssignResultDto result = service.bulkAssign(
                List.of(moved.getUuid(), already.getUuid(), moved.getUuid()), target.getUuid());

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(1);
        verify(alliesService).assignAdvisor(moved.getUuid(), target.getUuid());
        verify(alliesService, never()).assignAdvisor(already.getUuid(), target.getUuid());
    }

    @Test
    void systemTarget_is422() {
        Promoter institucion = promoter(true, true);
        when(promoterRepository.findByUuid(institucion.getUuid())).thenReturn(Optional.of(institucion));

        assertThatThrownBy(() -> service.bulkAssign(List.of(UUID.randomUUID()), institucion.getUuid()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("promoter.system_not_assignable");
        verify(alliesService, never()).assignAdvisor(any(), any());
    }

    @Test
    void inactiveTarget_is422() {
        Promoter gone = promoter(false, false);
        when(promoterRepository.findByUuid(gone.getUuid())).thenReturn(Optional.of(gone));

        assertThatThrownBy(() -> service.bulkAssign(List.of(UUID.randomUUID()), gone.getUuid()))
                .hasMessage("promoter.inactive");
    }

    @Test
    void unknownAlly_is404() {
        Promoter target = promoter(true, false);
        UUID missing = UUID.randomUUID();
        when(promoterRepository.findByUuid(target.getUuid())).thenReturn(Optional.of(target));
        when(allyRepository.findByUuid(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.bulkAssign(List.of(missing), target.getUuid()))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("ally.not_found");
    }

    private static Promoter promoter(boolean active, boolean system) {
        Promoter p = new Promoter();
        p.setId(java.util.concurrent.ThreadLocalRandom.current().nextLong(1, 1_000_000));
        p.setUuid(UUID.randomUUID());
        p.setDisplayName("Asesor");
        p.setActive(active);
        p.setSystem(system);
        return p;
    }

    private static Ally ally(Promoter advisor) {
        Ally a = new Ally();
        a.setUuid(UUID.randomUUID());
        a.setCapturedByPromoter(advisor);
        return a;
    }
}

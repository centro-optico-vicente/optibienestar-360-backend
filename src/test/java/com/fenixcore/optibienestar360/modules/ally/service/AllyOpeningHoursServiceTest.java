package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.modules.ally.dto.OpeningHourDto;
import com.fenixcore.optibienestar360.modules.ally.dto.OpeningHoursRequest;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyOpeningHour;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser.AllyRole;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyOpeningHourRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AllyOpeningHoursService}: the portal write is for
 * an active OWNER only, an invalid week is rejected before anything is
 * deleted, and a valid week replaces the stored one.
 */
@ExtendWith(MockitoExtension.class)
class AllyOpeningHoursServiceTest {

    @Mock private AllyRepository allyRepository;
    @Mock private AllyOpeningHourRepository hourRepository;
    @Mock private AllyUserRepository allyUserRepository;

    private final UUID actorUuid = UUID.randomUUID();
    private final Ally ally = ally();

    private AllyOpeningHoursService sut() {
        return new AllyOpeningHoursService(allyRepository, hourRepository, allyUserRepository);
    }

    @Test
    void replaceForOwner_staffIsForbidden() {
        when(allyUserRepository.findActiveByAllyUuidAndUserUuid(ally.getUuid(), actorUuid))
                .thenReturn(Optional.of(membership(AllyRole.STAFF)));

        assertThatThrownBy(() -> sut().replaceForOwner(ally.getUuid(), actorUuid, week()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("ally.opening_hours.not_allowed");
        verify(hourRepository, never()).deleteWeek(any());
    }

    @Test
    void replaceForOwner_foreignAllyIsForbidden() {
        when(allyUserRepository.findActiveByAllyUuidAndUserUuid(ally.getUuid(), actorUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut().replaceForOwner(ally.getUuid(), actorUuid, week()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getForMember_viewerCanRead() {
        when(allyUserRepository.findActiveByAllyUuidAndUserUuid(ally.getUuid(), actorUuid))
                .thenReturn(Optional.of(membership(AllyRole.VIEWER)));
        when(allyRepository.findByUuid(ally.getUuid())).thenReturn(Optional.of(ally));
        when(hourRepository.findWeek(ally.getId())).thenReturn(List.of(hour(1, "08:00", "12:00")));

        List<OpeningHourDto> week = sut().getForMember(ally.getUuid(), actorUuid);

        assertThat(week).singleElement().satisfies(h -> {
            assertThat(h.dayOfWeek()).isEqualTo(1);
            assertThat(h.opensAt()).isEqualTo(LocalTime.of(8, 0));
        });
    }

    @Test
    void replace_overlappingWeekIsRejectedBeforeDeleting() {
        when(allyRepository.findByUuid(ally.getUuid())).thenReturn(Optional.of(ally));
        OpeningHoursRequest overlapping = new OpeningHoursRequest(List.of(
                new OpeningHoursRequest.Range(1, LocalTime.of(8, 0), LocalTime.of(12, 0)),
                new OpeningHoursRequest.Range(1, LocalTime.of(11, 0), LocalTime.of(13, 0))));

        assertThatThrownBy(() -> sut().replace(ally.getUuid(), overlapping))
                .hasMessage("ally.opening_hours.overlap");
        verify(hourRepository, never()).deleteWeek(any());
    }

    @Test
    void replaceForOwner_ownerReplacesTheWeek() {
        when(allyUserRepository.findActiveByAllyUuidAndUserUuid(ally.getUuid(), actorUuid))
                .thenReturn(Optional.of(membership(AllyRole.OWNER)));
        when(allyRepository.findByUuid(ally.getUuid())).thenReturn(Optional.of(ally));
        when(hourRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<OpeningHourDto> saved = sut().replaceForOwner(ally.getUuid(), actorUuid, week());

        verify(hourRepository).deleteWeek(ally.getId());
        assertThat(saved).extracting(OpeningHourDto::dayOfWeek).containsExactly(1, 1, 6);
    }

    // ─── fixtures ───────────────────────────────────────────────────────────────

    private static OpeningHoursRequest week() {
        return new OpeningHoursRequest(List.of(
                new OpeningHoursRequest.Range(6, LocalTime.of(8, 0), LocalTime.of(12, 0)),
                new OpeningHoursRequest.Range(1, LocalTime.of(14, 0), LocalTime.of(18, 0)),
                new OpeningHoursRequest.Range(1, LocalTime.of(8, 0), LocalTime.of(12, 0))));
    }

    private static Ally ally() {
        Ally a = new Ally();
        a.setId(7L);
        a.setUuid(UUID.randomUUID());
        return a;
    }

    private AllyUser membership(AllyRole role) {
        AllyUser m = new AllyUser();
        m.setAlly(ally);
        m.setAllyRole(role);
        return m;
    }

    private AllyOpeningHour hour(int day, String opens, String closes) {
        AllyOpeningHour h = new AllyOpeningHour();
        h.setAlly(ally);
        h.setDayOfWeek((short) day);
        h.setOpensAt(LocalTime.parse(opens));
        h.setClosesAt(LocalTime.parse(closes));
        return h;
    }
}

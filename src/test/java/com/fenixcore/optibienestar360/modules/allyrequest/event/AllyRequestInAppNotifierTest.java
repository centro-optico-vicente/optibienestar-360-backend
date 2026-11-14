package com.fenixcore.optibienestar360.modules.allyrequest.event;

import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.notification.types.RegistrationApprovedNotification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Approving an ally request publishes {@code REGISTRATION_APPROVED} to the ally's OWNER login(s). */
@ExtendWith(MockitoExtension.class)
class AllyRequestInAppNotifierTest {

    @Mock private AllyRepository allyRepository;
    @Mock private AllyUserRepository allyUserRepository;
    @Mock private RegistrationApprovedNotification registrationApproved;

    private AllyRequestInAppNotifier notifier() {
        return new AllyRequestInAppNotifier(allyRepository, allyUserRepository, registrationApproved);
    }

    @Test
    void approved_publishesToTheOwners() {
        UUID allyUuid = UUID.randomUUID();
        Ally ally = new Ally();
        ally.setId(11L);
        when(allyRepository.findByUuid(allyUuid)).thenReturn(Optional.of(ally));
        when(allyUserRepository.findByAllyIdAndActiveTrue(eq(11L), any(Sort.class)))
                .thenReturn(List.of(member(1L, AllyUser.AllyRole.OWNER), member(2L, AllyUser.AllyRole.STAFF)));

        notifier().onEvent(event(AllyRequestEvent.Kind.APPROVED, allyUuid));

        verify(registrationApproved).publishAfterCommit(RegistrationApprovedNotification.Kind.ALLY, allyUuid,
                "Óptica Sol", Set.of(1L), AllyRequestInAppNotifier.SOURCE_MODULE, "es");
    }

    @Test
    void recipients_fallBackToEveryLogin_whenNoneIsOwner() {
        assertThat(AllyRequestInAppNotifier.recipients(List.of(
                member(3L, AllyUser.AllyRole.STAFF), member(4L, AllyUser.AllyRole.VIEWER))))
                .containsExactly(3L, 4L);
    }

    @Test
    void otherKinds_publishNothing() {
        notifier().onEvent(event(AllyRequestEvent.Kind.REJECTED, UUID.randomUUID()));
        notifier().onEvent(event(AllyRequestEvent.Kind.RECEIVED, UUID.randomUUID()));

        verifyNoInteractions(allyRepository, allyUserRepository, registrationApproved);
    }

    @Test
    void neverPropagatesAFailure() {
        when(allyRepository.findByUuid(any())).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> notifier().onEvent(event(AllyRequestEvent.Kind.APPROVED, UUID.randomUUID())))
                .doesNotThrowAnyException();
        verifyNoInteractions(registrationApproved);
    }

    private static AllyUser member(Long userId, AllyUser.AllyRole role) {
        User user = new User();
        user.setId(userId);
        AllyUser member = new AllyUser();
        member.setUser(user);
        member.setAllyRole(role);
        return member;
    }

    private static AllyRequestEvent event(AllyRequestEvent.Kind kind, UUID allyUuid) {
        return new AllyRequestEvent(kind, allyUuid, "Óptica Sol", "Ana Ruiz", "ana@example.com", null, null,
                null, null, null, null, "es", false);
    }
}

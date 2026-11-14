package com.fenixcore.optibienestar360.modules.affiliation.event;

import com.fenixcore.optibienestar360.modules.affiliation.entity.AffiliationRequest;
import com.fenixcore.optibienestar360.modules.affiliation.repository.AffiliationRequestRepository;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.notification.types.RegistrationApprovedNotification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Approving an affiliation request publishes {@code REGISTRATION_APPROVED} to the applicant's login. */
@ExtendWith(MockitoExtension.class)
class AffiliationRequestInAppNotifierTest {

    @Mock private AffiliationRequestRepository affiliationRequestRepository;
    @Mock private RegistrationApprovedNotification registrationApproved;

    private AffiliationRequestInAppNotifier notifier() {
        return new AffiliationRequestInAppNotifier(affiliationRequestRepository, registrationApproved);
    }

    @Test
    void approved_publishesToTheApplicant() {
        UUID requestUuid = UUID.randomUUID();
        User user = new User();
        user.setId(21L);
        AffiliationRequest request = new AffiliationRequest();
        request.setUser(user);
        when(affiliationRequestRepository.findByUuid(requestUuid)).thenReturn(Optional.of(request));

        notifier().onEvent(event(AffiliationRequestEvent.Kind.APPROVED, requestUuid));

        verify(registrationApproved).publishAfterCommit(RegistrationApprovedNotification.Kind.MEMBER, requestUuid,
                "Juan Pérez", List.of(21L), AffiliationRequestInAppNotifier.SOURCE_MODULE, "es");
    }

    @Test
    void approvedWithoutUser_publishesNothing() {
        UUID requestUuid = UUID.randomUUID();
        when(affiliationRequestRepository.findByUuid(requestUuid)).thenReturn(Optional.of(new AffiliationRequest()));

        notifier().onEvent(event(AffiliationRequestEvent.Kind.APPROVED, requestUuid));

        verifyNoInteractions(registrationApproved);
    }

    @Test
    void otherKinds_publishNothing() {
        notifier().onEvent(event(AffiliationRequestEvent.Kind.REJECTED, UUID.randomUUID()));
        notifier().onEvent(event(AffiliationRequestEvent.Kind.RECEIVED, UUID.randomUUID()));

        verifyNoInteractions(affiliationRequestRepository, registrationApproved);
    }

    @Test
    void neverPropagatesAFailure() {
        when(affiliationRequestRepository.findByUuid(any())).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> notifier().onEvent(event(AffiliationRequestEvent.Kind.APPROVED, UUID.randomUUID())))
                .doesNotThrowAnyException();
    }

    private static AffiliationRequestEvent event(AffiliationRequestEvent.Kind kind, UUID requestUuid) {
        return new AffiliationRequestEvent(kind, requestUuid, "PUBLIC", "juan@example.com", "Juan Pérez", "es",
                "Plan Plus", null, null, null, false);
    }
}

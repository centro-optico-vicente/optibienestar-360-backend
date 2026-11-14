package com.fenixcore.optibienestar360.modules.affiliation.event;

import com.fenixcore.optibienestar360.modules.affiliation.repository.AffiliationRequestRepository;
import com.fenixcore.optibienestar360.modules.notification.types.RegistrationApprovedNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * In-app notice of an approved affiliation request (hub ADR 0039): the
 * applicant's login gets {@code REGISTRATION_APPROVED} ({@code kind=MEMBER}).
 * On approval the request always has a user — the one its draft created, or
 * the one the approval just created.
 *
 * <p>Runs synchronously inside the approval transaction only to read the
 * recipient; the row itself is written after commit by
 * {@code UserNotificationPublisher.publishAfterCommit}. Any failure is logged
 * and never affects the approval. The approval email
 * ({@link AffiliationRequestNotifier}) is unchanged.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AffiliationRequestInAppNotifier {

    static final String SOURCE_MODULE = "affiliation_request";

    private final AffiliationRequestRepository affiliationRequestRepository;
    private final RegistrationApprovedNotification registrationApproved;

    @EventListener
    public void onEvent(AffiliationRequestEvent event) {
        if (event.kind() != AffiliationRequestEvent.Kind.APPROVED) {
            return;
        }
        try {
            affiliationRequestRepository.findByUuid(event.requestUuid())
                    .filter(request -> request.getUser() != null)
                    .ifPresent(request -> registrationApproved.publishAfterCommit(
                            RegistrationApprovedNotification.Kind.MEMBER, event.requestUuid(), event.fullName(),
                            List.of(request.getUser().getId()), SOURCE_MODULE, event.locale()));
        } catch (RuntimeException ex) {
            log.error("Failed to schedule the in-app approval notice of affiliation request {}",
                    event.requestUuid(), ex);
        }
    }
}

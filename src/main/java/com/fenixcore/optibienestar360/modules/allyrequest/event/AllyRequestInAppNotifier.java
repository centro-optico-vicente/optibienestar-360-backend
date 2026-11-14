package com.fenixcore.optibienestar360.modules.allyrequest.event;

import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.modules.notification.types.RegistrationApprovedNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * In-app notice of an approved ally request (hub ADR 0036 / 0039): the ally's
 * OWNER login(s) get {@code REGISTRATION_APPROVED} ({@code kind=ALLY}). Falls
 * back to every active login of the ally when none is OWNER.
 *
 * <p>Runs synchronously inside the approval transaction only to read the
 * recipients while the ally is loaded; the row itself is written after commit
 * by {@code UserNotificationPublisher.publishAfterCommit}. Any failure is
 * logged and never affects the approval. The approval email
 * ({@link AllyRequestNotifier}) is unchanged.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AllyRequestInAppNotifier {

    static final String SOURCE_MODULE = "ally_request";

    private final AllyRepository allyRepository;
    private final AllyUserRepository allyUserRepository;
    private final RegistrationApprovedNotification registrationApproved;

    @EventListener
    public void onEvent(AllyRequestEvent event) {
        if (event.kind() != AllyRequestEvent.Kind.APPROVED) {
            return;
        }
        try {
            allyRepository.findByUuid(event.allyUuid()).ifPresent(ally -> {
                Set<Long> recipients = recipients(
                        allyUserRepository.findByAllyIdAndActiveTrue(ally.getId(), Sort.unsorted()));
                registrationApproved.publishAfterCommit(RegistrationApprovedNotification.Kind.ALLY,
                        event.allyUuid(), event.allyName(), recipients, SOURCE_MODULE, event.locale());
            });
        } catch (RuntimeException ex) {
            log.error("Failed to schedule the in-app approval notice of ally request {}", event.allyUuid(), ex);
        }
    }

    /** OWNER logins; every active login when the ally has no OWNER. */
    static Set<Long> recipients(List<AllyUser> members) {
        Set<Long> owners = new LinkedHashSet<>();
        Set<Long> all = new LinkedHashSet<>();
        for (AllyUser member : members) {
            if (member.getUser() == null) {
                continue;
            }
            all.add(member.getUser().getId());
            if (member.getAllyRole() == AllyUser.AllyRole.OWNER) {
                owners.add(member.getUser().getId());
            }
        }
        return owners.isEmpty() ? all : owners;
    }
}

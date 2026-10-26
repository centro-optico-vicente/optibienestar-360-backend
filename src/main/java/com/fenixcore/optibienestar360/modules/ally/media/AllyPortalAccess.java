package com.fenixcore.optibienestar360.modules.ally.media;

import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser.AllyRole;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.modules.media.MediaAction;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Membership-based access of the ally portal to a gallery (hub ADR 0027): any
 * active member reads, OWNER and STAFF write, VIEWER never writes — the same
 * rule as proposing offers ({@code AllyProductsProposeService}).
 */
@Component
@RequiredArgsConstructor
class AllyPortalAccess {

    private final AllyUserRepository allyUserRepository;

    void require(UUID allyUuid, CustomUserDetails actor, MediaAction action) {
        AllyUser membership = actor == null ? null
                : allyUserRepository.findActiveByAllyUuidAndUserUuid(allyUuid, actor.getUuid()).orElse(null);
        if (membership == null) {
            throw new AccessDeniedException("media.forbidden");
        }
        if (action != MediaAction.READ && membership.getAllyRole() == AllyRole.VIEWER) {
            throw new AccessDeniedException("media.forbidden");
        }
    }
}

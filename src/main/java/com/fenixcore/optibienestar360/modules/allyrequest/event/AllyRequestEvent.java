package com.fenixcore.optibienestar360.modules.allyrequest.event;

import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.Segment;

import java.util.UUID;

/**
 * Something happened to an ally request (hub ADR 0036). Published inside the
 * transaction; {@link AllyRequestNotifier} sends the emails after commit.
 *
 * @param email         the applicant / contact email (may be null for an
 *                      imported ally without one)
 * @param activationUrl "create your password" link of an invitation
 */
public record AllyRequestEvent(
        Kind kind,
        UUID allyUuid,
        String allyName,
        String contactName,
        String email,
        Segment segment,
        Ally.RegistrationChannel channel,
        String promoterName,
        String note,
        String reason,
        String activationUrl,
        String locale,
        /** On APPROVED: whether the ally was published right away (onboarding policy) instead of waiting for staff. */
        boolean published
) {
    public enum Kind { RECEIVED, APPROVED, REJECTED, INVITED }
}

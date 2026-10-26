package com.fenixcore.optibienestar360.modules.affiliation.event;

import java.util.UUID;

/**
 * Published when an affiliation request is received, approved or rejected;
 * {@link AffiliationRequestNotifier} emails the applicant after commit and
 * {@link AffiliationRequestStaffNotifier} tells staff about a new one.
 * Carries plain values (not the entity) so the listeners need no session.
 *
 * <p>{@code activationUrl} is set only on an APPROVED assisted request without
 * password: the "create your password" link that replaces the login link.</p>
 */
public record AffiliationRequestEvent(
        Kind kind,
        UUID requestUuid,
        String channel,
        String email,
        String fullName,
        String locale,
        String planName,
        String advisorName,
        String reason,
        String activationUrl
) {

    public enum Kind { RECEIVED, APPROVED, REJECTED }
}

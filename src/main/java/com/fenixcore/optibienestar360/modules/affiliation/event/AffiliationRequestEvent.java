package com.fenixcore.optibienestar360.modules.affiliation.event;

/**
 * Published when an affiliation request is received, approved or rejected;
 * {@link AffiliationRequestNotifier} emails the applicant after commit.
 * Carries plain values (not the entity) so the listener needs no session.
 */
public record AffiliationRequestEvent(
        Kind kind,
        String email,
        String fullName,
        String locale,
        String planName,
        String advisorName,
        String reason
) {

    public enum Kind { RECEIVED, APPROVED, REJECTED }
}

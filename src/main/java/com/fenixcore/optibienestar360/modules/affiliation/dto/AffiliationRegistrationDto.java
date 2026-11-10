package com.fenixcore.optibienestar360.modules.affiliation.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The caller's own registration ({@code GET /v1/me/affiliation-request}, hub
 * ADR 0037): everything saved so far, so the portal can resume where the
 * person left off. {@code contractAccepted} is true while the accepted
 * contract still matches the current data (a change to a field printed in
 * it invalidates the acceptance).
 */
public record AffiliationRegistrationDto(
        UUID uuid,
        @Display(value = Display.Kind.ENUM, enumScope = "affiliation_request.status") String status,
        String firstName,
        String lastName,
        String documentType,
        String documentNumber,
        @Display(Display.Kind.DATE) LocalDate birthDate,
        String email,
        String phone,
        @Display DisplayRef plan,
        UUID termsVersionUuid,
        boolean contractAccepted,
        AffiliationRequestPaymentDto payment,
        List<FamilyMember> family,
        String advisorName,
        String referralCode,
        @Display(Display.Kind.DATETIME) Instant submittedAt,
        String rejectionReason,
        boolean retainFromCleanup,
        String retainNote,
        @Display(Display.Kind.DATETIME) Instant retainedAt
) {

    /** A relative typed in the family step. */
    public record FamilyMember(
            String firstName,
            String lastName,
            String documentType,
            String documentNumber,
            LocalDate birthDate,
            String relationship,
            String phone,
            String email
    ) {}
}

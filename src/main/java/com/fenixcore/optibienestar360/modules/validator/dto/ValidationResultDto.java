package com.fenixcore.optibienestar360.modules.validator.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fenixcore.optibienestar360.core.display.Display;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response body for {@code GET /v1/me/allies/validations/{document}}. Designed to
 * be small and JSON-serializable so the same shape can be cached verbatim
 * in Redis with a 60-second TTL.
 *
 * <p>{@code status} is the headline answer for the ally counter operator:
 * only {@link ValidationStatus#ACTIVE} authorizes applying the benefit.
 * It carries a localized {@code status_Display} sibling (hub ADR 0014) so
 * the counter can show the reason without a client-side label map.</p>
 *
 * <p>{@code cached} is non-authoritative — purely diagnostic.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ValidationResultDto(
        @Display(Display.Kind.ENUM) ValidationStatus status,
        String documentType,
        String documentNumber,

        // Member identity — populated when found, even if membership not
        // active, so the ally can confirm the human at the counter is the
        // person we have on file.
        UUID memberUuid,
        String memberFullName,

        // Plan + membership detail — populated only when a membership row
        // exists.
        UUID planUuid,
        String planCode,
        String planName,

        UUID membershipUuid,
        @Display(Display.Kind.DATE) LocalDate enrolledAt,
        @Display(Display.Kind.DATE) LocalDate nextDueDate,
        @Display(Display.Kind.DATE) LocalDate lastPaidThrough,
        Integer gracePeriodDays,

        // Who is at the counter (V195): set when the document belongs to a
        // beneficiary — the coverage above is then their holder's
        // (memberFullName is the holder). Null when the holder presented.
        UUID beneficiaryUuid,
        String beneficiaryFullName,
        String beneficiaryRelationship,

        // Everyone the membership covers besides the holder, so the ally can
        // book the usage on the person who actually consumed.
        List<CoveredPerson> beneficiaries,

        // Diagnostic only — tells the ally portal whether this response
        // came from cache (true) or was a fresh DB roundtrip (false).
        boolean cached
) {

    /** An active beneficiary of the validated membership. */
    public record CoveredPerson(UUID beneficiaryUuid, String fullName, String relationship) {}

    public enum ValidationStatus {
        /** Document doesn't match any person on file. */
        NOT_FOUND,
        /** Person exists but is not enrolled as a Member. */
        NOT_ENROLLED,
        /** Member exists but has no active membership (e.g. only cancelled history). */
        NO_ACTIVE_MEMBERSHIP,
        /** Active membership is healthy — benefit may be applied. */
        ACTIVE,
        /** Membership is past due, within grace — benefit cannot be applied. */
        SUSPENDED,
        /** Membership is past grace — benefit cannot be applied. */
        EXPIRED,
        /** Membership was administratively cancelled (terminal). */
        CANCELED
    }
}

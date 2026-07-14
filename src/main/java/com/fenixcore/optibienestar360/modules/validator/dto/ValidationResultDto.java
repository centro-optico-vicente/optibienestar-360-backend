package com.fenixcore.optibienestar360.modules.validator.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Response body for {@code GET /v1/ally/validate/{document}}. Designed to
 * be small and JSON-serializable so the same shape can be cached verbatim
 * in Redis with a 60-second TTL.
 *
 * <p>{@code status} is the headline answer for the ally counter operator:
 * only {@link ValidationStatus#ACTIVE} authorizes applying the benefit.
 * Other terminal values explain why so the operator can tell the affiliate
 * exactly what's wrong (suspended for non-payment, expired beyond grace,
 * cancelled, never enrolled, document not registered at all).</p>
 *
 * <p>{@code cached} is non-authoritative — purely diagnostic, indicates
 * whether the answer came from Redis or was freshly computed. Used by
 * monitoring to compute the cache hit ratio.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ValidationResultDto(
        ValidationStatus status,
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
        LocalDate enrolledAt,
        LocalDate nextDueDate,
        LocalDate lastPaidThrough,
        Integer gracePeriodDays,

        // Diagnostic only — tells the ally portal whether this response
        // came from cache (true) or was a fresh DB roundtrip (false).
        boolean cached
) {

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

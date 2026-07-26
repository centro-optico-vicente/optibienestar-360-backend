package com.fenixcore.optibienestar360.modules.member.dto;

import com.fenixcore.optibienestar360.modules.member.entity.Beneficiary.Relationship;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Output DTO for {@code GET /v1/admin/members/{memberUuid}/beneficiaries}
 * (list) and {@code GET .../beneficiaries/{uuid}} (detail). Flattens the
 * linked Person into the most useful fields for the family-grid surface;
 * full demographic editing happens through the Person resolver inside the
 * service rather than via this DTO.
 *
 * <p>{@code extraInscriptionPaid} (v2) tracks whether the one-time
 * inscription fee triggered when this beneficiary exceeded the plan's
 * {@code included_beneficiaries} cap has been collected.
 * {@code inscriptionPaymentUuid} references the payment that settles it — the
 * PENDING charge auto-generated when the beneficiary exceeds the included cap,
 * or {@code null} for included beneficiaries (no extra fee due).</p>
 */
public record BeneficiaryDto(
        UUID uuid,
        UUID memberUuid,

        // Person — flattened
        UUID personUuid,
        String firstName,
        String middleName,
        String lastName,
        String secondLastName,
        String fullName,
        String documentType,
        String documentNumber,
        LocalDate birthDate,
        String phone,
        String email,

        Relationship relationship,
        boolean extraInscriptionPaid,
        UUID inscriptionPaymentUuid,

        boolean active,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}

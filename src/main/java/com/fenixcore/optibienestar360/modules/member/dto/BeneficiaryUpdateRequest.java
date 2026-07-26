package com.fenixcore.optibienestar360.modules.member.dto;

import com.fenixcore.optibienestar360.core.validation.VenezuelanDocumentNumber;
import com.fenixcore.optibienestar360.modules.member.entity.Beneficiary.Relationship;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Payload for {@code PUT /v1/admin/members/{memberUuid}/beneficiaries/{uuid}}.
 * PATCH semantics — non-null fields applied. The (member, person) pair
 * itself is immutable on update; to swap a beneficiary, delete + add (the
 * v2 "sustitución de beneficiarios" UX flow).
 *
 * <p>The extra-inscription payment link is set automatically by {@code add}
 * when the beneficiary exceeds the plan's included cap, so it is not editable
 * here; {@code extraInscriptionPaid} stays toggleable so the admin can mark the
 * charge collected once the linked payment is approved.</p>
 */
public record BeneficiaryUpdateRequest(
        // Person fields — flow through to the linked persons row
        @Size(max = 50) String firstName,
        @Size(max = 50) String middleName,
        @Size(max = 50) String lastName,
        @Size(max = 50) String secondLastName,

        @Pattern(regexp = "^[VE]$", message = "{validation.document_type.format}") String documentType,
        @Size(max = 20) @VenezuelanDocumentNumber String documentNumber,

        LocalDate birthDate,
        @Size(max = 30) String phone,
        @Email @Size(max = 255) String email,

        // Beneficiary-specific
        Relationship relationship,
        Boolean extraInscriptionPaid,
        Boolean active,
        String status
) {}

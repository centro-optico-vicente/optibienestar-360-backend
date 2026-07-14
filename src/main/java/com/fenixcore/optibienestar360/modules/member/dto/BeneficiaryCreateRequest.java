package com.fenixcore.optibienestar360.modules.member.dto;

import com.fenixcore.optibienestar360.core.validation.VenezuelanDocumentNumber;
import com.fenixcore.optibienestar360.modules.member.entity.Beneficiary.Relationship;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Payload for {@code POST /v1/admin/members/{memberUuid}/beneficiaries}.
 *
 * <p>Person fields come inline so the admin can register a brand-new
 * beneficiary (e.g. a newborn child of the titular) in a single call. The
 * service runs them through {@link
 * com.fenixcore.optibienestar360.modules.person.service.PersonService#findOrCreate}
 * — when a person with the same cédula already exists (was a User, a
 * Member titular, or a beneficiary of another titular), that row is reused
 * instead of duplicated. UNIQUE(member_id, person_id) on V18 means
 * re-adding an existing (member, person) pair reactivates the soft-deleted
 * row instead of inserting a duplicate.</p>
 *
 * <p>No {@code @MinimumAge} here — beneficiaries are allowed to be minors
 * ({@code Relationship.CHILD} is the most common case). The titular
 * carries the adult-age constraint via {@link MemberCreateRequest}.</p>
 */
public record BeneficiaryCreateRequest(
        // Person fields — required for the beneficiary identity
        @NotBlank @Size(max = 50) String firstName,
        @Size(max = 50) String middleName,
        @NotBlank @Size(max = 50) String lastName,
        @Size(max = 50) String secondLastName,

        @NotBlank @Pattern(regexp = "^[VE]$", message = "{validation.document_type.format}")
        String documentType,
        @NotBlank @Size(max = 20) @VenezuelanDocumentNumber
        String documentNumber,

        LocalDate birthDate,
        @Size(max = 30) String phone,
        @Email @Size(max = 255) String email,

        @NotNull Relationship relationship,
        Boolean extraInscriptionPaid
) {}

package com.fenixcore.optibienestar360.modules.affiliation.dto;

import com.fenixcore.optibienestar360.core.validation.VenezuelanDocumentNumber;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Body of {@code PUT /v1/admin/affiliation-requests/{uuid}}: the reviewer's
 * correction of a PENDING request (full replace of the editable data, same
 * rules as the public form). The password, the referral and the contract
 * evidence are not editable. {@code comment} is an optional note for the log.
 */
public record AffiliationRequestUpdateRequest(
        @NotBlank @Size(max = 50) String firstName,
        @NotBlank @Size(max = 50) String lastName,
        @NotBlank @Pattern(regexp = "^[VE]$") String documentType,
        @NotBlank @Size(max = 20) @VenezuelanDocumentNumber String documentNumber,
        @NotNull @Past LocalDate birthDate,
        @NotBlank @Email @Size(max = 254) String email,
        @Size(max = 30) String phone,
        @NotNull UUID planUuid,
        @Size(max = 2000) String comment
) {}

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
 * Body of {@code POST /v1/public/affiliation-requests} — the public
 * affiliation form (advisor QR → {@code /afiliarse}). Same fields and rules
 * as the former {@code POST /v1/public/affiliations}: {@code password}
 * follows the user-creation policy (8–128 characters) and is stored only as
 * its BCrypt hash; {@code termsVersionUuid} is the current public AFILIADO
 * contract the applicant accepted.
 */
public record AffiliationRequestCreateRequest(
        @NotBlank @Size(max = 50) String firstName,
        @NotBlank @Size(max = 50) String lastName,
        @NotBlank @Pattern(regexp = "^[VE]$") String documentType,
        @NotBlank @Size(max = 20) @VenezuelanDocumentNumber String documentNumber,
        @NotNull @Past LocalDate birthDate,
        @NotBlank @Email @Size(max = 254) String email,
        @Size(max = 30) String phone,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotNull UUID planUuid,
        @Size(max = 20) String referralCode,
        @NotNull UUID termsVersionUuid
) {}

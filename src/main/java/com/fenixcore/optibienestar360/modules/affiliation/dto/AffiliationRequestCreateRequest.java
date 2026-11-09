package com.fenixcore.optibienestar360.modules.affiliation.dto;

import com.fenixcore.optibienestar360.core.validation.VenezuelanDocumentNumber;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
 *
 * <p><b>Step 1 of the step-by-step registration (hub ADR 0037 §2):</b> only the
 * identity fields are required; {@code password} is optional (without one the
 * person signs in with an access link). When {@code birthDate}, {@code planUuid}
 * and {@code termsVersionUuid} are all present the request is the legacy
 * one-shot form (a PENDING request, nothing else to complete); otherwise a
 * DRAFT is created, the login is opened and the response carries the session.</p>
 *
 * <p>{@code payment} (optional) reports the payment of the plan's inscription
 * fee; its proof file is the {@code support} part when the form is sent as
 * {@code multipart/form-data}. Approval registers it as the membership's
 * inscription payment.</p>
 */
public record AffiliationRequestCreateRequest(
        @NotBlank @Size(max = 50) String firstName,
        @NotBlank @Size(max = 50) String lastName,
        @NotBlank @Pattern(regexp = "^[VE]$") String documentType,
        @NotBlank @Size(max = 20) @VenezuelanDocumentNumber String documentNumber,
        @Past LocalDate birthDate,
        @NotBlank @Email @Size(max = 254) String email,
        @Size(max = 30) String phone,
        @Size(min = 8, max = 128) String password,
        UUID planUuid,
        @Size(max = 20) String referralCode,
        UUID termsVersionUuid,
        @Valid AffiliationPaymentRequest payment
) {}

package com.fenixcore.optibienestar360.modules.affiliation.dto;

import com.fenixcore.optibienestar360.core.validation.VenezuelanDocumentNumber;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Body of {@code POST /v1/me/advisor/affiliation-requests} — the assisted
 * affiliation: the advisor fills the same form as {@code /afiliarse} for a
 * family, on their own device, and the family reads and accepts the contract
 * there. Same fields and rules as {@link AffiliationRequestCreateRequest}
 * except:
 * <ul>
 *   <li>no {@code referralCode} — the request is attributed to the caller;</li>
 *   <li>{@code password} is optional (8–128 characters when sent). Without
 *   it, approval emails a link to create the password;</li>
 *   <li>{@code contractAccepted} must be {@code true}: the advisor confirms
 *   the person read and accepted the contract shown on screen.</li>
 * </ul>
 */
public record AssistedAffiliationRequestCreateRequest(
        @NotBlank @Size(max = 50) String firstName,
        @NotBlank @Size(max = 50) String lastName,
        @NotBlank @Pattern(regexp = "^[VE]$") String documentType,
        @NotBlank @Size(max = 20) @VenezuelanDocumentNumber String documentNumber,
        @NotNull @Past LocalDate birthDate,
        @NotBlank @Email @Size(max = 254) String email,
        @Size(max = 30) String phone,
        @Size(min = 8, max = 128) String password,
        @NotNull UUID planUuid,
        @NotNull UUID termsVersionUuid,
        @NotNull @AssertTrue Boolean contractAccepted
) {}

package com.fenixcore.optibienestar360.modules.affiliation.dto;

import com.fenixcore.optibienestar360.modules.member.dto.BeneficiaryCreateRequest;
import com.fenixcore.optibienestar360.core.validation.VenezuelanDocumentNumber;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Body of {@code PATCH /v1/me/affiliation-request/{step}} (hub ADR 0037 §2).
 * One record for the four steps; each step reads only its own fields and
 * ignores the rest:
 * <ul>
 *   <li>{@code personal}: {@code firstName}, {@code lastName}, {@code birthDate}, {@code phone};
 *   an advisor's assisted draft that has no login yet also reads {@code documentType},
 *   {@code documentNumber} and {@code email} (to correct a typo) — the person's own
 *   flow ignores those three, their identity is the login's</li>
 *   <li>{@code plan}: {@code planUuid}, {@code termsVersionUuid}, {@code contractAccepted}</li>
 *   <li>{@code payment}: {@code payment} (also as the {@code request} part of a multipart body with an optional {@code support} proof)</li>
 *   <li>{@code family}: {@code family} — the full list, replacing the previous one</li>
 * </ul>
 */
public record AffiliationDraftStepRequest(
        @Size(max = 50) String firstName,
        @Size(max = 50) String lastName,
        @Past LocalDate birthDate,
        @Size(max = 30) String phone,
        UUID planUuid,
        UUID termsVersionUuid,
        Boolean contractAccepted,
        @Valid AffiliationPaymentRequest payment,
        @Valid List<BeneficiaryCreateRequest> family,
        @Pattern(regexp = "^[VE]$") String documentType,
        @Size(max = 20) @VenezuelanDocumentNumber String documentNumber,
        @Email @Size(max = 254) String email
) {}

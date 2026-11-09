package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.core.validation.VenezuelanDocumentNumber;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Payload for {@code POST /v1/admin/advisors/with-user} (hub ADR 0037 §5):
 * creates the login (role ASESOR) and the advisor profile in one step.
 * {@code password} is optional — without it the person gets an invitation
 * email with the link to create it. {@code displayName} defaults to the
 * full name; {@code referralCode} is generated when omitted, as in
 * {@link PromoterCreateRequest}.
 */
public record PromoterWithUserCreateRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(max = 50) String firstName,
        @NotBlank @Size(max = 50) String lastName,
        @NotBlank @Pattern(regexp = "^[VE]$", message = "{validation.document_type.format}") String documentType,
        @NotBlank @Size(max = 20) @VenezuelanDocumentNumber String documentNumber,
        @Size(max = 30) String phone,
        @Size(min = 8, max = 128) String password,

        @Size(max = 120) String displayName,
        String description,
        @Pattern(regexp = "^$|^[A-Z0-9-]{4,20}$", message = "{promoter.referral_code.format}")
        String referralCode,
        UUID promoterTypeUuid,
        LocalDate joinedAt,
        @Size(max = 120) String assignedZone
) {}

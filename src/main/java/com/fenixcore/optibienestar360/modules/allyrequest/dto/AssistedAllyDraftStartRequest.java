package com.fenixcore.optibienestar360.modules.allyrequest.dto;

import com.fenixcore.optibienestar360.core.validation.PhoneNumber;
import com.fenixcore.optibienestar360.core.validation.VenezuelanDocumentNumber;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.Segment;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Body of {@code POST /v1/me/advisor/ally-requests/drafts} — the first step of an advisor-assisted ally
 * capture (hub ADR 0037 §5): who the business or professional is and who the contact person is. Nothing
 * else is needed to save it; the type, RIF / profession, city and the rest of the profile follow through the
 * ally's profile endpoints ({@code /v1/me/allies/{uuid}/profile}, licenses, opening hours). No password and
 * no login: the contact person gets a login, with an "create your password" link, when the advisor submits.
 * The email is required because that link goes there.
 */
public record AssistedAllyDraftStartRequest(
        @NotNull Segment segment,
        UUID allyTypeUuid,

        /** Business name; required for a commercial ally (B2B), ignored for a professional (the person's name is used). */
        @Size(max = 200) String name,
        UUID professionUuid,

        @NotBlank @Size(max = 50) String firstName,
        @NotBlank @Size(max = 50) String lastName,
        @NotBlank @Pattern(regexp = "^[VE]$", message = "{validation.document_type.format}") String documentType,
        @NotBlank @Size(max = 20) @VenezuelanDocumentNumber String documentNumber,
        @NotBlank @Size(max = 30) @PhoneNumber String mobile,
        @NotBlank @Email @Size(max = 255) String email,
        UUID cityUuid,
        @Size(max = 1000) String benefitNote
) {}

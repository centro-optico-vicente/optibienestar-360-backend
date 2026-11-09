package com.fenixcore.optibienestar360.modules.allyrequest.dto;

import com.fenixcore.optibienestar360.core.validation.VenezuelanDocumentNumber;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.Segment;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Public "Solicitar alianza" form ({@code POST /v1/public/ally-requests}, hub
 * ADR 0036), <b>step 1 of the step-by-step registration</b> (hub ADR 0037 §2):
 * only {@code segment}, the name (B2B), the contact person, the mobile and the
 * email are needed; {@code password} is optional (without one the person signs
 * in with an access link) and the type, RIF / profession and city arrive in
 * later steps through the ally's own profile. When they are all sent the
 * request is complete and goes to review right away (the previous one-step
 * form). Formerly one step: the common fields are always required; a commercial
 * ally (B2B) adds its name and RIF, a professional (B2P) its profession. The
 * segment-conditional checks live in the service (422 with a message key).
 * {@code homepage} is a honeypot the form renders hidden: humans leave it
 * empty.
 */
public record AllyRequestCreateRequest(
        @NotNull Segment segment,
        UUID allyTypeUuid,

        // B2B
        @Size(max = 200) String name,
        @Pattern(regexp = "^[JVEGP]$", message = "{validation.tax_document_type.format}") String taxDocumentType,
        @Size(max = 20) @Pattern(regexp = "^[0-9]*$", message = "{validation.tax_document_number.digits}") String taxDocumentNumber,

        // B2P
        UUID professionUuid,

        // Contact / holder person (the login needs a person, which needs a cédula)
        @NotBlank @Size(max = 50) String firstName,
        @NotBlank @Size(max = 50) String lastName,
        @NotBlank @Pattern(regexp = "^[VE]$", message = "{validation.document_type.format}") String documentType,
        @NotBlank @Size(max = 20) @VenezuelanDocumentNumber String documentNumber,
        @NotBlank @Size(max = 30) String mobile,
        @NotBlank @Email @Size(max = 255) String email,
        UUID cityUuid,
        @Size(min = 8, max = 128) String password,

        @Size(max = 20) String referralCode,
        @Size(max = 1000) String benefitNote,
        Boolean acceptedPortalTerms,
        @Size(max = 200) String homepage
) {}

package com.fenixcore.optibienestar360.modules.affiliation.dto;

import com.fenixcore.optibienestar360.core.validation.VenezuelanDocumentNumber;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /v1/me/advisor/affiliation-requests/drafts} — the first
 * step of an assisted affiliation saved as a DRAFT (hub ADR 0037 §5): only who
 * the person is. Birth date, plan and contract, payment and family follow with
 * {@code PATCH /drafts/{uuid}/{step}}. No password: the person is created on
 * approval and gets a link to create it, as with the one-shot assisted request.
 */
public record AssistedAffiliationDraftStartRequest(
        @NotBlank @Size(max = 50) String firstName,
        @NotBlank @Size(max = 50) String lastName,
        @NotBlank @Pattern(regexp = "^[VE]$") String documentType,
        @NotBlank @Size(max = 20) @VenezuelanDocumentNumber String documentNumber,
        @NotBlank @Email @Size(max = 254) String email,
        @Size(max = 30) String phone
) {}

package com.fenixcore.optibienestar360.modules.terms.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Body of {@code POST /v1/public/terms/AFILIADO/preview}: what the applicant
 * has typed so far on the public affiliation form. Every field is optional —
 * the preview runs while the form is being filled, and a missing value
 * renders as its pending note. Only length-bounded here; the real validation
 * happens on submit ({@code POST /v1/public/affiliation-requests}).
 */
public record MemberTermsPreviewRequest(
        @Size(max = 50) String firstName,
        @Size(max = 50) String lastName,
        @Size(max = 2) String documentType,
        @Size(max = 20) String documentNumber,
        LocalDate birthDate,
        UUID planUuid,
        @Size(max = 20) String referralCode
) {}

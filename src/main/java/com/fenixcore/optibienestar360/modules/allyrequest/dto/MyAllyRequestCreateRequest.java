package com.fenixcore.optibienestar360.modules.allyrequest.dto;

import com.fenixcore.optibienestar360.core.validation.PhoneNumber;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.Segment;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Step 1 of the ally registration for someone who already has an account
 * ({@code POST /v1/me/ally-requests}): the {@link AllyRequestCreateRequest}
 * fields minus the contact person, the email and the password, which come from
 * the caller's account. {@code mobile} is optional when the account already
 * has a phone.
 */
public record MyAllyRequestCreateRequest(
        @NotNull Segment segment,
        UUID allyTypeUuid,

        // B2B
        @Size(max = 200) String name,
        @Pattern(regexp = "^[JVEGP]$", message = "{validation.tax_document_type.format}") String taxDocumentType,
        @Size(max = 20) @Pattern(regexp = "^[0-9]*$", message = "{validation.tax_document_number.digits}") String taxDocumentNumber,

        // B2P
        UUID professionUuid,

        @Size(max = 30) @PhoneNumber String mobile,
        UUID cityUuid,

        @Size(max = 20) String referralCode,
        @Size(max = 1000) String benefitNote,
        Boolean acceptedPortalTerms,

        /** The caller's refresh token, revoked when the session switches to the pending role (optional). */
        String refreshToken
) {}

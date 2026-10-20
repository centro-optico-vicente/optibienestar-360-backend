package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.core.validation.VenezuelanDocumentNumber;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload for {@code PUT /v1/admin/advisors/{promoterUuid}/references/{uuid}}.
 * PATCH semantics — non-null fields are applied; a blank string clears the
 * optional ones ({@code documentType}/{@code documentNumber}/{@code phone}).
 */
public record PromoterReferenceUpdateRequest(
        @Size(max = 50) String firstName,
        @Size(max = 50) String lastName,

        @Pattern(regexp = "^$|^[VE]$", message = "{validation.document_type.format}")
        String documentType,
        @Size(max = 20) @VenezuelanDocumentNumber String documentNumber,

        @Size(max = 30) String phone
) {}

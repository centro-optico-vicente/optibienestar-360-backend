package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.core.validation.VenezuelanDocumentNumber;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload for {@code POST /v1/admin/promoters/{promoterUuid}/references}.
 * The cédula is optional but comes as a pair (both or neither) — the service
 * rejects a half pair with 422 {@code promoter_reference.document.incomplete}.
 */
public record PromoterReferenceCreateRequest(
        @NotBlank @Size(max = 50) String firstName,
        @NotBlank @Size(max = 50) String lastName,

        @Pattern(regexp = "^$|^[VE]$", message = "{validation.document_type.format}")
        String documentType,
        @Size(max = 20) @VenezuelanDocumentNumber String documentNumber,

        @Size(max = 30) String phone
) {}

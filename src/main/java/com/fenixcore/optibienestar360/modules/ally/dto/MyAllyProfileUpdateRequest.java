package com.fenixcore.optibienestar360.modules.ally.dto;

import com.fenixcore.optibienestar360.core.validation.NotFutureYear;
import com.fenixcore.optibienestar360.core.validation.PhoneNumber;
import com.fenixcore.optibienestar360.core.validation.WebUrl;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Portal profile edit ({@code PUT /v1/me/allies/{allyUuid}/profile}, hub ADR
 * 0036). PATCH semantics: {@code null} = unchanged, blank text = cleared.
 * The other segment's fields are rejected (422
 * {@code ally.segment.field_not_allowed}); {@code name} only while the request
 * is PENDING (422 {@code ally.profile.name_locked}). The registration data
 * ({@code allyTypeUuid}, RIF for B2B, {@code professionUuid} for B2P) is also
 * editable only while PENDING (422 {@code ally.profile.registration_locked}).
 * Phones, website and foundation year are format-checked (backend#413): free text such as "hola" or
 * "mi sitio web" would reach the public directory. The RIF number is digits only — the frontend drops the
 * hyphen of the {@code 12345678-9} format before sending.
 */
public record MyAllyProfileUpdateRequest(
        @Size(max = 200) String name,
        @Email @Size(max = 255) String email,
        @Size(max = 30) @PhoneNumber String phone,
        @Size(max = 30) @PhoneNumber String whatsapp,
        @Size(max = 255) @WebUrl String website,
        @Size(max = 255) String instagram,
        @Size(max = 255) String facebook,
        @Size(max = 2000) String address,
        UUID cityUuid,
        @Size(max = 500) String googleMapsUrl,
        @Size(max = 4000) String description,

        // B2B
        @Size(max = 200) String tradeName,
        @Size(max = 100) String commercialZone,
        @Min(1800) @NotFutureYear Short foundedYear,
        @PositiveOrZero Integer cashRegistersCount,
        @PositiveOrZero Integer branchesCount,
        Boolean hasPos,
        @Size(max = 100) String representativePosition,

        // B2P
        @Size(max = 200) String hostName,
        @Size(max = 20) String officeNumber,
        @Size(max = 30) @PhoneNumber String appointmentPhone,
        @Size(max = 150) String assistantName,

        // Registration data (hub ADR 0037 §2): only while the request is PENDING
        UUID allyTypeUuid,
        @jakarta.validation.constraints.Pattern(regexp = "^[JVEGP]$", message = "{validation.tax_document_type.format}") String taxDocumentType,
        @Size(max = 20) @jakarta.validation.constraints.Pattern(regexp = "^[0-9]*$", message = "{validation.tax_document_number.digits}") String taxDocumentNumber,
        UUID professionUuid
) {}

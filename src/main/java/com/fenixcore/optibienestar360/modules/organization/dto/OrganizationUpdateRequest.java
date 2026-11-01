package com.fenixcore.optibienestar360.modules.organization.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * PATCH-style: {@code officialCurrencyUuid}/{@code referenceCurrencyUuid}
 * omitted (null) leave the existing value unchanged — same degrade-to-no-op
 * convention as {@code CountryUpdateRequest.officialCurrencyUuid}. Unlike
 * {@code Country}, both FKs are {@code optional = false} on the entity, so
 * an explicit unresolvable UUID is a validation error, never a way to null
 * the field out.
 */
public record OrganizationUpdateRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 200) String legalName,
        @Size(max = 20) String taxIdentifier,
        @Size(max = 255) String logoKey,
        @Size(max = 30) String whatsapp,
        @Size(max = 255) String instagram,
        @Size(max = 255) String facebook,
        UUID officialCurrencyUuid,
        UUID referenceCurrencyUuid,
        boolean autoApproveCommissions,
        @Size(max = 80) String registryState,
        @Size(max = 80) String signingCity,
        @Size(max = 150) String serviceArea,
        @Size(max = 500) String address,
        @Email @Size(max = 254) String email,
        @Size(max = 100) String phones,
        @Size(max = 200) String signatoryName,
        @Pattern(regexp = "^$|^[JVEGP]$", message = "{validation.tax_document_type.format}")
        String signatoryDocumentType,
        @Size(max = 20) String signatoryDocumentNumber,
        @Size(max = 100) String signatoryPosition
) {}

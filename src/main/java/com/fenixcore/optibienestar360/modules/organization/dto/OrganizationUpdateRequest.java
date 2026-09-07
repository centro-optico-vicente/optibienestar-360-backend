package com.fenixcore.optibienestar360.modules.organization.dto;

import jakarta.validation.constraints.NotBlank;
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
        UUID officialCurrencyUuid,
        UUID referenceCurrencyUuid
) {}

package com.fenixcore.optibienestar360.modules.ally.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Payload for {@code PUT /v1/admin/allies/{uuid}} — PATCH semantics: only
 * fields present (non-null) are applied. Setting {@code professionUuids} to a
 * non-null list <em>replaces</em> the current set of professions; leaving it
 * null keeps the existing set untouched.
 */
public record AllyUpdateRequest(
        @Size(max = 200) String name,
        UUID allyTypeUuid,

        @Pattern(regexp = "^[JVEGP]$", message = "{validation.tax_document_type.format}")
        String taxDocumentType,
        @Size(max = 20) String taxDocumentNumber,

        @Email @Size(max = 255) String email,
        @Size(max = 30) String phone,
        @Size(max = 255) String website,
        @Size(max = 30) String whatsapp,
        @Size(max = 255) String instagram,
        @Size(max = 255) String facebook,

        String address,
        UUID cityUuid,
        @Size(max = 500) String googleMapsUrl,

        @Size(max = 500) String logoUrl,
        String description,
        LocalDate joinedAt,

        Boolean published,
        Instant publishedAt,

        Boolean active,
        String status,

        List<UUID> professionUuids
) {}

package com.fenixcore.optibienestar360.modules.ally.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Payload for {@code POST /v1/admin/allies}.
 *
 * <p>Tax document (RIF) and city are optional; if {@code taxDocumentType} is
 * present, {@code taxDocumentNumber} must be too (the V11 CHECK
 * {@code chk_allies_tax_document} enforces both-or-none on the DB side; this
 * record can leave them split since the validation message is friendlier from
 * the constraint trigger).</p>
 *
 * <p>{@code specialtyUuids} populates the {@code @ManyToMany} pivote
 * {@code ally_specialties}. Empty list = no specialties.</p>
 */
public record AllyCreateRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull UUID allyTypeUuid,

        @Pattern(regexp = "^[JVEGP]$", message = "{validation.tax_document_type.format}")
        String taxDocumentType,
        @Size(max = 20) String taxDocumentNumber,

        @Email @Size(max = 255) String email,
        @Size(max = 30) String phone,
        @Size(max = 255) String website,

        String address,
        UUID cityUuid,

        @Size(max = 500) String logoUrl,
        String description,
        LocalDate joinedAt,

        Boolean published,
        Instant publishedAt,

        List<UUID> specialtyUuids
) {}

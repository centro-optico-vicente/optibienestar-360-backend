package com.fenixcore.optibienestar360.modules.ally.dto;

import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.Segment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Payload for {@code PUT /v1/admin/allies/{uuid}} — PATCH semantics: only
 * fields present (non-null) are applied. Setting {@code professionUuids} to a
 * non-null list <em>replaces</em> the current set of professions; leaving it
 * null keeps the existing set untouched. Same for {@code allyTypeUuids},
 * except the resulting set is validated to never end up empty (an ally must
 * always keep at least one type).
 */
public record AllyUpdateRequest(
        @Size(max = 200) String name,
        List<UUID> allyTypeUuids,

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
        @Pattern(regexp = "^$|^[A-Za-z0-9-]{4,20}$", message = "{validation.referralCode.format}") String referralCode,

        @Size(max = 500) String logoUrl,
        String description,
        LocalDate joinedAt,

        Boolean published,
        Instant publishedAt,


        // ─── Segment + holder person (V211, hub ADR 0031) ──────────────────
        /** B2B / B2P. Null = unchanged. */
        Segment segment,
        /** Professional (B2P, required) or signing representative (B2B); find-or-create by cédula. Null = unchanged. */
        @Valid AllyHolderPersonRequest holder,

        // ─── B2P practice site — rejected for a B2B ally ───────────────────
        /** Clinic the professional practices at, when it is an ally (B2B). Null = unchanged. */
        UUID hostAllyUuid,
        @Size(max = 200) String hostName,
        @Size(max = 20) String officeNumber,
        @Size(max = 30) String appointmentPhone,
        @Size(max = 150) String assistantName,

        // ─── B2B commercial data — rejected for a B2P ally ─────────────────
        @Size(max = 100) String commercialZone,
        @Size(max = 200) String tradeName,
        @Min(1800) @Max(2100) Short foundedYear,
        @PositiveOrZero Integer cashRegistersCount,
        @PositiveOrZero Integer branchesCount,
        Boolean hasPos,
        @Size(max = 100) String representativePosition,

        // ─── Agreement file + representative (V179) — allyNumber is never accepted ─
        /** Advisor (promoter) who captured the ally; null = none (create) / unchanged (update). */
        UUID capturedByPromoterUuid,
        // Deprecated since V211: use holder.
        @Size(max = 210) String representativeName,
        @Pattern(regexp = "^[JVEGP]$", message = "{validation.tax_document_type.format}")
        String representativeDocumentType,
        @Size(max = 20) String representativeDocumentNumber,

        Boolean active,
        String status,

        List<UUID> professionUuids
) {}

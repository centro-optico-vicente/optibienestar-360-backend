package com.fenixcore.optibienestar360.modules.ally.dto;

import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.Segment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
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
 * <p>{@code professionUuids} populates the {@code @ManyToMany} pivote
 * {@code ally_professions}. Empty list = no professions.</p>
 *
 * <p>{@code allyTypeUuids} populates the {@code @ManyToMany} pivot
 * {@code ally_ally_types} — at least one type is required (business rule,
 * not a DB-level NOT NULL since the relation is now M:N).</p>
 */
public record AllyCreateRequest(
        @NotBlank @Size(max = 200) String name,
        @NotEmpty List<UUID> allyTypeUuids,

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
        @Pattern(regexp = "^[A-Za-z0-9-]{4,20}$", message = "{validation.referralCode.format}") String referralCode,

        @Size(max = 500) String logoUrl,
        String description,
        LocalDate joinedAt,

        Boolean published,
        Instant publishedAt,


        // ─── Segment + holder person (V209, hub ADR 0031) ──────────────────
        /** B2B / B2P. Null = derived from the types (all B2P → B2P, else B2B). */
        Segment segment,
        /** Professional (B2P, required) or signing representative (B2B); find-or-create by cédula.  */
        @Valid AllyHolderPersonRequest holder,

        // ─── B2P practice site — rejected for a B2B ally ───────────────────
        /** Clinic the professional practices at, when it is an ally (B2B).  */
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
        /** @deprecated V209 — use {@code holder}. */
        @Size(max = 210) String representativeName,
        @Pattern(regexp = "^[JVEGP]$", message = "{validation.tax_document_type.format}")
        String representativeDocumentType,
        @Size(max = 20) String representativeDocumentNumber,

        List<UUID> professionUuids
) {}

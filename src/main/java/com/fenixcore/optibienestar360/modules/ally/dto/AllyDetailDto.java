package com.fenixcore.optibienestar360.modules.ally.dto;

import com.fenixcore.optibienestar360.modules.catalog.dto.AllyTypeDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.CityDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.MedicalSpecialtyDto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Full per-ally view returned by {@code GET /v1/admin/allies/{uuid}} and
 * {@code GET /v1/public/allies/{uuid}}. Embeds the catalog DTOs for
 * ally_type, city, and specialties so the frontend can render the full
 * ficha without follow-up calls.
 *
 * <p>Users / services / agreements are NOT inlined — they have dedicated
 * sub-resources ({@code /allies/{id}/users}, {@code /services},
 * {@code /agreements}) with their own pagination. Counts are exposed here
 * so the frontend can show "3 active services" badges without querying the
 * sub-resources.</p>
 */
public record AllyDetailDto(
        UUID uuid,
        String name,
        AllyTypeDto allyType,

        // ─── Tax identity ──────────────────────────────────────────────────
        String taxDocumentType,
        String taxDocumentNumber,

        // ─── Contact + address ─────────────────────────────────────────────
        String email,
        String phone,
        String website,
        String address,
        CityDto city,

        // ─── Branding ──────────────────────────────────────────────────────
        String logoUrl,
        String description,
        LocalDate joinedAt,

        // ─── Publishing ────────────────────────────────────────────────────
        boolean published,
        Instant publishedAt,

        // ─── Medical specialties (ally_specialties pivot) ──────────────────
        List<MedicalSpecialtyDto> specialties,

        // ─── Aggregated summary (sub-lists not inlined; sub-resources handle that) ─
        int activeUsersCount,
        int activeServicesCount,
        int activeAgreementsCount,

        // ─── Audit ─────────────────────────────────────────────────────────
        boolean active,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}

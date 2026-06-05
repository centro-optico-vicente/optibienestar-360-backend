package com.fenixcore.optisaludplus.modules.ally.dto;

import com.fenixcore.optisaludplus.modules.catalog.dto.AllyTypeDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.CityDto;
import com.fenixcore.optisaludplus.modules.catalog.dto.MedicalSpecialtyDto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Full per-ally view returned by {@code GET /v1/admin/allies/{uuid}} and
 * {@code GET /v1/public/allies/{uuid}}. Embeds the catalog DTOs for
 * ally_type, city, and specialties so the frontend can render the ficha
 * without follow-up calls.
 *
 * <p>Users / services / agreements are NOT inlined — they have dedicated
 * sub-resources ({@code /allies/{id}/users}, {@code /services},
 * {@code /agreements}) with their own pagination. Counts are exposed here
 * so the frontend can show "3 servicios activos" badges without querying
 * the sub-resources.</p>
 */
public record AllyDetailDto(
        UUID uuid,
        String name,
        AllyTypeDto allyType,

        // ─── Identidad fiscal ──────────────────────────────────────────────
        String taxDocumentType,
        String taxDocumentNumber,

        // ─── Contacto + dirección ──────────────────────────────────────────
        String email,
        String phone,
        String website,
        String address,
        CityDto city,

        // ─── Branding ──────────────────────────────────────────────────────
        String logoUrl,
        String description,
        LocalDate joinedAt,

        // ─── Publicación ───────────────────────────────────────────────────
        boolean published,
        Instant publishedAt,

        // ─── Especialidades médicas (ally_specialties pivote) ──────────────
        List<MedicalSpecialtyDto> specialties,

        // ─── Resumen agregado (no inlines las sub-listas) ──────────────────
        int activeUsersCount,
        int activeServicesCount,
        int activeAgreementsCount,

        // ─── Audit ─────────────────────────────────────────────────────────
        boolean active,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}

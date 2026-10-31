package com.fenixcore.optibienestar360.modules.ally.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.Segment;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.catalog.dto.AllyTypeDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.CityDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.ProfessionDto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Full per-ally view returned by {@code GET /v1/admin/allies/{uuid}} and
 * {@code GET /v1/public/allies/{uuid}}. Embeds the catalog DTOs for
 * ally_type, city, and professions so the frontend can render the full
 * ficha without follow-up calls.
 *
 * <p>{@code fileNumber} is the agreement file number
 * "[CÓDIGO DE ASESOR]-[Nº DE ALIADO]": the capturing promoter's referral
 * code, a dash and the ally number zero-padded to 4 digits
 * ({@code "ABC123-0007"}); just the padded number ({@code "0007"}) when no
 * promoter captured the ally. See {@code AllyMapper#fileNumber}.
 * {@code capturedByPromoter} serializes as {@code capturedByPromoter_Uuid} +
 * {@code capturedByPromoter_Display} (ADR 0014).</p>
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
        List<AllyTypeDto> allyTypes,
        /** B2B / B2P (V211, hub ADR 0031); localized {@code segment_Display}. */
        @Display(value = Display.Kind.ENUM, enumScope = "ally_type.segment") Segment segment,
        /** Professional (B2P) or signing representative (B2B); {@code null} until loaded. */
        AllyHolderPersonDto holder,

        // ─── B2P practice site (V211) ──────────────────────────────────────
        /** Host clinic when it is an ally: {@code hostAlly_Uuid} + {@code hostAlly_Display}. */
        @Display DisplayRef hostAlly,
        String hostName,
        String officeNumber,
        String appointmentPhone,
        String assistantName,

        // ─── B2B commercial data (V211) ────────────────────────────────────
        String commercialZone,
        String tradeName,
        Short foundedYear,
        Integer cashRegistersCount,
        Integer branchesCount,
        Boolean hasPos,
        String representativePosition,

        // ─── Agreement file (V179) — read-only ─────────────────────────────
        Integer allyNumber,
        String fileNumber,
        @Display DisplayRef capturedByPromoter,

        // ─── Legal representative / signing physician (V179) — DEPRECATED (V211: holder) ─
        String representativeName,
        String representativeDocumentType,
        String representativeDocumentNumber,

        // ─── Tax identity ──────────────────────────────────────────────────
        String taxDocumentType,
        String taxDocumentNumber,

        // ─── Contact + address ─────────────────────────────────────────────
        String email,
        String phone,
        String website,
        String whatsapp,
        String instagram,
        String facebook,
        String address,
        CityDto city,
        String googleMapsUrl,
        /** Promotion code the ally hands out (V175); attribution only. */
        String referralCode,

        // ─── Branding ──────────────────────────────────────────────────────
        String logoUrl,
        String description,
        LocalDate joinedAt,

        // ─── Publishing ────────────────────────────────────────────────────
        boolean published,
        Instant publishedAt,

        // ─── Medical professions (ally_professions pivot) ──────────────────
        List<ProfessionDto> professions,

        // ─── Aggregated summary (sub-lists not inlined; sub-resources handle that) ─
        int activeUsersCount,
        int activeProductsCount,
        int activeAgreementsCount,

        // ─── Audit ─────────────────────────────────────────────────────────
        boolean active,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}

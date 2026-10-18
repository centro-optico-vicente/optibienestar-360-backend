package com.fenixcore.optibienestar360.modules.member.dto;

import com.fenixcore.optibienestar360.modules.catalog.dto.CityDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.GenderDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.MaritalStatusDto;
import com.fenixcore.optibienestar360.modules.catalog.dto.OccupationDto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Full per-member view for {@code GET /v1/admin/members/{uuid}}. Inlines
 * the catalog DTOs for gender / marital status / city / occupation so the
 * frontend can render the ficha without follow-up calls. Beneficiaries /
 * documents / medical record are exposed via their dedicated sub-resources
 * (own bullets) — only their active counts are surfaced here so the UI can
 * show "3 beneficiaries" badges without querying.
 *
 * <p>Family-nucleus slot summary (V178): {@code baseSlotsUsed} /
 * {@code extraSlotsUsed} count active beneficiaries per slot type;
 * {@code baseSlotsTotal} ({@code included_beneficiaries}) and
 * {@code extraSlotsTotal} ({@code max - included}) come from the plan of the
 * active membership — both {@code null} without one, and
 * {@code extraSlotsTotal} {@code null} when the plan has no cap.</p>
 */
public record MemberDetailDto(
        UUID uuid,

        // ─── Person fields ─────────────────────────────────────────────────
        UUID personUuid,
        String firstName,
        String middleName,
        String lastName,
        String secondLastName,
        String fullName,

        String documentType,
        String documentNumber,
        String taxDocumentType,
        String taxDocumentNumber,

        LocalDate birthDate,
        GenderDto gender,
        MaritalStatusDto maritalStatus,

        // ─── Inscription-form demographics (planilla) ──────────────────────
        String birthplace,
        Integer numberOfChildren,
        String spouseName,

        String phone,
        String landlinePhone,
        String email,
        String locale,
        String address,
        CityDto city,

        // ─── Member-specific ───────────────────────────────────────────────
        OccupationDto occupation,
        String employerName,
        String jobPosition,
        String employerAddress,
        LocalDate enrolledAt,
        String notes,

        // ─── Aggregated sub-collection counts ──────────────────────────────
        int activeBeneficiariesCount,
        int activeDocumentsCount,
        boolean hasMedicalRecord,

        // ─── Family-nucleus slots (V178) ───────────────────────────────────
        int baseSlotsUsed,
        Integer baseSlotsTotal,
        int extraSlotsUsed,
        Integer extraSlotsTotal,

        // ─── Promoter attribution — null when unlinked ─────────────────────
        UUID currentPromoterUuid,
        String currentPromoterName,

        // ─── Audit ─────────────────────────────────────────────────────────
        boolean active,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Instant confirmedAt
) {}

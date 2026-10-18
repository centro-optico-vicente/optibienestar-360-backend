package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Output DTO for {@code GET /v1/admin/promoters} (list) and
 * {@code GET /v1/admin/promoters/{uuid}} (detail).
 *
 * <p>{@code _Display} convention (hub ADR 0014): {@code @Display} foreign
 * keys serialize as {@code <rel>_Uuid} + {@code <rel>_Display}, and
 * {@code @Display} scalars keep their raw value and gain a localized
 * {@code <field>_Display} sibling. {@code user}/{@code person} carry the
 * identity links (both {@code null} on the {@code INSTITUCION} system row);
 * {@code promoterType} is nullable on legacy rows. {@code rank} (V101, the
 * "cargo" — independent of {@code promoterType}) and {@code supervisor}
 * (the live pointer, {@code null} = top of the chain) are read-only here;
 * changing them goes through the dedicated hierarchy endpoints ({@code
 * POST /v1/admin/promoters/{uuid}/assign-supervisor}).</p>
 *
 * <p>Dossier fields (V182): {@code joinedAt}/{@code assignedZone}
 * (registration) and the executive profile ({@code educationLevel} +
 * {@code educationLevel_Display}, profession, experience, last job,
 * vehicle). {@code identity} is filled only by the detail endpoint.</p>
 */
public record PromoterDto(
        UUID uuid,
        String displayName,
        String description,
        String referralCode,
        @Display(Display.Kind.BOOLEAN) boolean system,

        @Display DisplayRef user,
        @Display DisplayRef person,
        @Display DisplayRef promoterType,
        @Display DisplayRef rank,
        @Display DisplayRef supervisor,

        String email,
        String phone,

        // ─── Dossier (V182) ────────────────────────────────────────────────
        @Display(Display.Kind.DATE) LocalDate joinedAt,
        String assignedZone,
        @Display(Display.Kind.ENUM) String educationLevel,
        String profession,
        Short yearsOfExperience,
        String lastCompany,
        String lastPosition,
        String vehicle,

        int totalReferrals,
        @Display(Display.Kind.MONEY) BigDecimal totalCommissionPaid,

        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(value = Display.Kind.ENUM, enumScope = "promoter.status") String status,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        @Display(Display.Kind.DATETIME) Instant updatedAt,

        /** Linked person's identity — detail only, {@code null} on the list. */
        PromoterIdentityDto identity
) {}

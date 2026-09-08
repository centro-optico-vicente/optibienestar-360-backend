package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;

import java.math.BigDecimal;
import java.time.Instant;
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

        int totalReferrals,
        @Display(Display.Kind.MONEY) BigDecimal totalCommissionPaid,

        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(value = Display.Kind.ENUM, enumScope = "promoter.status") String status,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        @Display(Display.Kind.DATETIME) Instant updatedAt
) {}

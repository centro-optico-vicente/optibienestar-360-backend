package com.fenixcore.optibienestar360.modules.member.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Compact projection for the admin members list. Person identity fields stay
 * flat (they are their own list columns); the foreign keys and
 * presentational scalars follow the {@code _Display} convention (hub ADR
 * 0014) — {@code @Display} FKs serialize as {@code <rel>_Uuid} +
 * {@code <rel>_Display}, {@code @Display} scalars keep their raw value and
 * gain a localized {@code <field>_Display} sibling.
 */
public record MemberListItemDto(
        UUID uuid,

        // Person — flat list columns
        String fullName,
        String documentType,
        String documentNumber,
        String phone,

        @Display DisplayRef city,

        @Display(Display.Kind.DATE) LocalDate enrolledAt,

        // Promoter attribution — null when unlinked
        @Display DisplayRef currentPromoter,

        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(value = Display.Kind.ENUM, enumScope = "member.status") String status,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        @Display(Display.Kind.DATETIME) Instant confirmedAt
) {}

package com.fenixcore.optibienestar360.modules.ally.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Compact projection for list / directory views. Avoids loading nested
 * collections (professions, users, services, agreements) — those come from
 * {@link AllyDetailDto} on the per-ally GET.
 *
 * <p>{@code _Display} convention (hub ADR 0014): {@code @Display} foreign
 * keys serialize as the flat pair {@code <rel>_Uuid} + {@code <rel>_Display},
 * and {@code @Display} scalars keep their raw value and gain a localized
 * {@code <field>_Display} sibling — both resolved from the request
 * {@code Locale} by {@code DisplayBeanSerializerModifier}. {@code _Display}
 * keys are output-only.</p>
 */
public record AllyListItemDto(
        UUID uuid,
        String name,

        List<String> allyTypeNames,
        @Display DisplayRef city,

        String taxDocumentType,
        String taxDocumentNumber,

        String logoUrl,
        String phone,

        @Display(Display.Kind.BOOLEAN) boolean published,
        @Display(Display.Kind.DATETIME) Instant publishedAt,

        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(value = Display.Kind.ENUM, enumScope = "ally.status") String status,

        @Display(Display.Kind.DATETIME) Instant createdAt
) {}

package com.fenixcore.optibienestar360.modules.ally.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.Segment;
import com.fenixcore.optibienestar360.modules.media.dto.PublicImageDto;

import java.util.List;
import java.util.UUID;

/**
 * Full per-ally view for the public detail page
 * ({@code GET /v1/public/allies/{uuid}}). Same sanitization rules as
 * {@link PublicAllyListItemDto} — admin / audit / financial / contact-channel
 * fields stay out — but adds three things the detail page needs:
 *
 * <ul>
 *   <li>Full {@code address} on top of the city label.</li>
 *   <li>List of medical profession names (just labels, no UUIDs) so the
 *       frontend can render them as tags.</li>
 *   <li>List of {@link PublicAllyProductDto} — filtered to only published +
 *       APPROVED + active offerings; everything else (PROPOSED / IN_REVIEW /
 *       REJECTED / REMOVED, unpublished, soft-deleted) is intentionally
 *       hidden from anonymous viewers.</li>
 * </ul>
 *
 * <p>Agreements / users / financial metadata are NOT exposed here — they
 * belong to admin-only surfaces.</p>
 */
public record PublicAllyDetailDto(
        UUID uuid,
        String name,
        List<String> allyTypeNames,
        /** Distinct segments ({@code B2B} / {@code B2P}) of the active ally types (V179). Superseded by {@code segment}. */
        List<String> allySegments,
        /** The ally's own segment (V211, hub ADR 0031); localized {@code segment_Display}. */
        @Display(value = Display.Kind.ENUM, enumScope = "ally_type.segment") Segment segment,
        /** B2P: clinic the professional practices at (the host ally's name or the free-text name). */
        String hostName,
        /** B2P: office (consultorio) number. */
        String officeNumber,
        /** B2P: phone for appointments. */
        String appointmentPhone,

        // Location
        String address,
        String cityName,

        // Branding + contact
        String logoUrl,
        String description,
        String website,
        String phone,
        String whatsapp,
        String instagram,
        String facebook,
        String googleMapsUrl,

        /** Weekly opening hours (V214, hub ADR 0031 §7), by day then opening time. */
        List<OpeningHourDto> openingHours,
        /** The same week as text, e.g. "Lun–Vie 08:00–12:00, 14:00–18:00"; null without hours. */
        String openingHoursLabel,

        // Professions (just labels)
        List<String> professionNames,

        // Public offerings (already filtered to APPROVED + published + active)
        List<PublicAllyProductDto> products,

        /** Same list as {@code products} — deprecated, kept while the landing moves over (ADR 0026). */
        @Deprecated
        List<PublicAllyProductDto> services,

        /** The ally's gallery in display order; {@code logoUrl}'s image is the primary one. */
        List<PublicImageDto> images
) {}

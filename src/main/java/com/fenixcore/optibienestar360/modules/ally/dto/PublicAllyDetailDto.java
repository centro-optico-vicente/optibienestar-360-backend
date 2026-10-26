package com.fenixcore.optibienestar360.modules.ally.dto;

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
        /** Distinct segments ({@code B2B} / {@code B2P}) of the active ally types (V179). */
        List<String> allySegments,

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

        // Professions (just labels)
        List<String> professionNames,

        // Public offerings (already filtered to APPROVED + published + active)
        List<PublicAllyProductDto> products,

        /** Same list as {@code products} — deprecated, kept while the landing moves over (ADR 0026). */
        @Deprecated
        List<PublicAllyProductDto> services,

        /** The ally's gallery in display order; {@code logoUrl}'s image is the primary one. */
        List<PublicImageDto> images
) {
    /** Fills the galleries (MapStruct builds this DTO without them). */
    public PublicAllyDetailDto withImages(List<PublicImageDto> images, List<PublicAllyProductDto> products) {
        return new PublicAllyDetailDto(uuid, name, allyTypeNames, allySegments, address, cityName, logoUrl, description,
                website, phone, whatsapp, instagram, facebook, googleMapsUrl, professionNames, products, products, images);
    }
}

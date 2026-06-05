package com.fenixcore.optisaludplus.modules.ally.dto;

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
 *   <li>List of medical specialty names (just labels, no UUIDs) so the
 *       frontend can render them as tags.</li>
 *   <li>List of {@link PublicAllyServiceDto} — filtered to only published +
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
        String allyTypeName,

        // Location
        String address,
        String cityName,

        // Branding + contact
        String logoUrl,
        String description,
        String website,
        String phone,

        // Specialties (just labels)
        List<String> specialtyNames,

        // Public offerings (already filtered to APPROVED + published + active)
        List<PublicAllyServiceDto> services
) {}

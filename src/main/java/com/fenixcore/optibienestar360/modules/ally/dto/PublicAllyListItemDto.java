package com.fenixcore.optibienestar360.modules.ally.dto;

import java.util.UUID;

/**
 * Sanitized projection of {@link com.fenixcore.optibienestar360.modules.ally.entity.Ally}
 * for the public directory ({@code GET /v1/public/allies}). Excludes
 * everything the directory's anonymous audience shouldn't see:
 *
 * <ul>
 *   <li>Tax document (RIF) — internal commercial identifier.</li>
 *   <li>Email — would be harvested by scrapers; the contact channel
 *       exposed is the phone.</li>
 *   <li>Audit / status / active / publishedAt — internal lifecycle.</li>
 *   <li>Manager / joinedAt / sub-collections — admin-only metadata.</li>
 * </ul>
 *
 * <p>{@code uuid} is exposed so the frontend can link to
 * {@code /v1/public/allies/{uuid}} (next bullet's detail endpoint).</p>
 */
public record PublicAllyListItemDto(
        UUID uuid,
        String name,

        String allyTypeName,
        String cityName,

        String logoUrl,
        String description,
        String website,
        String phone
) {}

package com.fenixcore.optisaludplus.modules.ally.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Compact projection for list / directory views. Avoids loading nested
 * collections (specialties, users, services, agreements) — those come from
 * {@link AllyDetailDto} on the per-ally GET.
 */
public record AllyListItemDto(
        UUID uuid,
        String name,

        // Flat fields from related entities for cheap rendering (no nested object
        // serialization, no extra JOINs beyond what JPA loads with the relation).
        UUID allyTypeUuid,
        String allyTypeName,

        UUID cityUuid,
        String cityName,

        String logoUrl,
        String phone,

        boolean published,
        Instant publishedAt,

        boolean active,
        String status,
        Instant createdAt
) {}

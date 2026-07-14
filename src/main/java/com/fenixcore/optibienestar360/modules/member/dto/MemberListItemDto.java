package com.fenixcore.optibienestar360.modules.member.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Compact projection for the admin members list. Person fields are flat
 * (extracted via the mapper) so the list renders cheap — no nested catalog
 * DTO serialization and no triggered lazy loads on N rows.
 */
public record MemberListItemDto(
        UUID uuid,

        // Person — flat
        String fullName,
        String documentType,
        String documentNumber,
        String phone,
        String cityName,

        // Member-specific
        LocalDate enrolledAt,

        // Audit
        boolean active,
        String status,
        Instant createdAt
) {}

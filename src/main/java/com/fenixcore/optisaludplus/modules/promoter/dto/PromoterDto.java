package com.fenixcore.optisaludplus.modules.promoter.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Output DTO for {@code GET /v1/admin/promoters} (list) and
 * {@code GET /v1/admin/promoters/{uuid}} (detail). Flat record with
 * person + user refs extracted as UUIDs so the admin UI renders without
 * a follow-up call.
 */
public record PromoterDto(
        UUID uuid,
        String displayName,
        String description,
        String referralCode,
        boolean system,

        // Identity links (null on the INSTITUCION system row)
        UUID userUuid,
        UUID personUuid,

        // Contact
        String email,
        String phone,

        // Snapshot counters
        int totalReferrals,
        BigDecimal totalCommissionPaid,

        // Audit + lifecycle status
        boolean active,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}

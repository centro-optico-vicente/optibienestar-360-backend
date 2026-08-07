package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.modules.promoter.entity.CollectionCommissionTier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Output DTO for the admin collection-commission-tier surface. */
public record CollectionCommissionTierDto(
        UUID uuid,
        String name,
        int maxDays,
        BigDecimal commissionPct,
        boolean active,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static CollectionCommissionTierDto from(CollectionCommissionTier t) {
        return new CollectionCommissionTierDto(
                t.getUuid(), t.getName(), t.getMaxDays(), t.getCommissionPct(),
                t.isActive(), t.getStatus(), t.getCreatedAt(), t.getUpdatedAt());
    }
}

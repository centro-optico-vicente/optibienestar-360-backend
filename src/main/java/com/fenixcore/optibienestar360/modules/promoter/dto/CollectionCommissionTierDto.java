package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.modules.promoter.entity.CollectionCommissionTier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Output DTO for the admin collection-commission-tier surface. Scalars carry a localized {@code _Display} sibling (ADR 0014). */
public record CollectionCommissionTierDto(
        UUID uuid,
        String name,
        int maxDays,
        @Display(Display.Kind.NUMBER) BigDecimal commissionPct,
        UUID promoterTypeUuid,
        String promoterTypeName,
        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(value = Display.Kind.ENUM, enumScope = "collection_commission_tier.status") String status,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        @Display(Display.Kind.DATETIME) Instant updatedAt
) {
    public static CollectionCommissionTierDto from(CollectionCommissionTier t) {
        return new CollectionCommissionTierDto(
                t.getUuid(), t.getName(), t.getMaxDays(), t.getCommissionPct(),
                t.getPromoterType() != null ? t.getPromoterType().getUuid() : null,
                t.getPromoterType() != null ? t.getPromoterType().getName() : null,
                t.isActive(), t.getStatus(), t.getCreatedAt(), t.getUpdatedAt());
    }
}

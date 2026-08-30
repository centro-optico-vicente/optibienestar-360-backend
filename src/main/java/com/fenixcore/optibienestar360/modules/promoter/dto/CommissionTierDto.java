package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan.PlanType;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionTier;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionTier.AppliesTo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Output DTO for the admin commission-tier surface. Scalars carry a localized {@code _Display} sibling (ADR 0014). */
public record CommissionTierDto(
        UUID uuid,
        String name,
        @Display(Display.Kind.ENUM) PlanType planType,
        UUID promoterTypeUuid,
        String promoterTypeName,
        int thresholdCount,
        @Display(Display.Kind.NUMBER) BigDecimal commissionPct,
        @Display(Display.Kind.MONEY) BigDecimal flatAmount,
        @Display(Display.Kind.ENUM) PeriodStrategy periodStrategy,
        @Display(Display.Kind.ENUM) AppliesTo appliesTo,
        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(value = Display.Kind.ENUM, enumScope = "commission_tier.status") String status,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        @Display(Display.Kind.DATETIME) Instant updatedAt
) {
    public static CommissionTierDto from(CommissionTier t) {
        return new CommissionTierDto(
                t.getUuid(), t.getName(), t.getPlanType(),
                t.getPromoterType() != null ? t.getPromoterType().getUuid() : null,
                t.getPromoterType() != null ? t.getPromoterType().getName() : null,
                t.getThresholdCount(),
                t.getCommissionPct(), t.getFlatAmount(), t.getPeriodStrategy(), t.getAppliesTo(),
                t.isActive(), t.getStatus(), t.getCreatedAt(), t.getUpdatedAt());
    }
}

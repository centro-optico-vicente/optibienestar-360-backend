package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.modules.membership.entity.Plan.PlanType;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionTier;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionTier.AppliesTo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Output DTO for the admin commission-tier surface. */
public record CommissionTierDto(
        UUID uuid,
        String name,
        PlanType planType,
        UUID promoterTypeUuid,
        String promoterTypeName,
        int thresholdCount,
        BigDecimal commissionPct,
        BigDecimal flatAmount,
        PeriodStrategy periodStrategy,
        AppliesTo appliesTo,
        boolean active,
        String status,
        Instant createdAt,
        Instant updatedAt
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

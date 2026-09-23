package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan.PlanType;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionTier;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionTier.AppliesTo;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionTier.BasisType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Output DTO for the admin commission-tier surface. Scalars carry a localized {@code _Display} sibling (ADR 0014). */
public record CommissionTierDto(
        UUID uuid,
        String name,
        String description,
        @Display(Display.Kind.ENUM) PlanType planType,
        /** M:N promoter-type scope (V137, hub plan Part F) — empty = applies to all. */
        List<DisplayRef> promoterTypes,
        int thresholdCount,
        @Display(Display.Kind.NUMBER) BigDecimal commissionPct,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "flatAmountCurrency_Code") BigDecimal flatAmount,
        @Display DisplayRef flatAmountCurrency,
        String flatAmountCurrency_Code,
        @Display(Display.Kind.ENUM) PeriodStrategy accrualPeriodStrategy,
        @Display(Display.Kind.ENUM) PeriodStrategy partialSettlementPeriodStrategy,
        @Display(Display.Kind.ENUM) PeriodStrategy finalSettlementPeriodStrategy,
        @Display(Display.Kind.ENUM) PeriodStrategy retroactiveSettlementPeriodStrategy,
        Short accrualPeriodAnchor,
        Short partialSettlementPeriodAnchor,
        Short finalSettlementPeriodAnchor,
        Short retroactiveSettlementPeriodAnchor,
        @Display(Display.Kind.ENUM) BasisType basis,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "thresholdAmountCurrency_Code") BigDecimal thresholdAmount,
        @Display DisplayRef thresholdAmountCurrency,
        String thresholdAmountCurrency_Code,
        @Display(Display.Kind.ENUM) AppliesTo appliesTo,
        /** Optional campaign anchor (V120) — {@code null} = a standing (non-campaign) tier. */
        @Display DisplayRef campaign,
        @Display(Display.Kind.DATETIME) OffsetDateTime startsAt,
        @Display(Display.Kind.DATETIME) OffsetDateTime endsAt,
        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(value = Display.Kind.ENUM, enumScope = "commission_tier.status") String status,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        @Display(Display.Kind.DATETIME) Instant updatedAt
) {
    public static CommissionTierDto from(CommissionTier t) {
        return new CommissionTierDto(
                t.getUuid(), t.getName(), t.getDescription(), t.getPlanType(),
                t.getPromoterTypes().stream().map(DisplayRefs::ref).toList(),
                t.getThresholdCount(),
                t.getCommissionPct(), t.getFlatAmount(),
                DisplayRefs.ref(t.getFlatAmountCurrency()),
                t.getFlatAmountCurrency() != null ? t.getFlatAmountCurrency().getCode() : null,
                t.getAccrualPeriodStrategy(), t.getPartialSettlementPeriodStrategy(),
                t.getFinalSettlementPeriodStrategy(), t.getRetroactiveSettlementPeriodStrategy(),
                t.getAccrualPeriodAnchor(), t.getPartialSettlementPeriodAnchor(),
                t.getFinalSettlementPeriodAnchor(), t.getRetroactiveSettlementPeriodAnchor(),
                t.getBasis(), t.getThresholdAmount(),
                DisplayRefs.ref(t.getThresholdAmountCurrency()),
                t.getThresholdAmountCurrency() != null ? t.getThresholdAmountCurrency().getCode() : null,
                t.getAppliesTo(),
                DisplayRefs.ref(t.getCampaign()), t.getStartsAt(), t.getEndsAt(),
                t.isActive(), t.getStatus(), t.getCreatedAt(), t.getUpdatedAt());
    }
}

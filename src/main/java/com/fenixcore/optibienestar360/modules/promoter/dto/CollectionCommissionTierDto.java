package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;
import com.fenixcore.optibienestar360.modules.promoter.entity.CollectionCommissionTier;
import com.fenixcore.optibienestar360.modules.promoter.entity.CollectionCommissionTier.Basis;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Output DTO for the admin collection-commission-tier surface. Scalars carry a localized {@code _Display} sibling (ADR 0014). */
public record CollectionCommissionTierDto(
        UUID uuid,
        String name,
        String description,
        @Display(Display.Kind.ENUM) Basis basis,
        Integer maxDays,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "minAmountCurrency_Code") BigDecimal minAmount,
        @Display DisplayRef minAmountCurrency,
        String minAmountCurrency_Code,
        @Display(Display.Kind.NUMBER) BigDecimal commissionPct,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "flatAmountCurrency_Code") BigDecimal flatAmount,
        @Display DisplayRef flatAmountCurrency,
        String flatAmountCurrency_Code,
        /** M:N promoter-type scope (V137, hub plan Part F) — empty = applies to all. */
        List<DisplayRef> promoterTypes,
        /** Settlement-frequency axes (Fase A, V148) — see {@link CommissionTierDto} for the same shape. */
        @Display(Display.Kind.ENUM) PeriodStrategy accrualPeriodStrategy,
        @Display(Display.Kind.ENUM) PeriodStrategy partialSettlementPeriodStrategy,
        @Display(Display.Kind.ENUM) PeriodStrategy finalSettlementPeriodStrategy,
        @Display(Display.Kind.ENUM) PeriodStrategy retroactiveSettlementPeriodStrategy,
        Short accrualPeriodAnchor,
        Short partialSettlementPeriodAnchor,
        Short finalSettlementPeriodAnchor,
        Short retroactiveSettlementPeriodAnchor,
        /** Optional campaign anchor (V126) — {@code null} = a standing tier. */
        @Display DisplayRef campaign,
        @Display(Display.Kind.DATETIME) OffsetDateTime startsAt,
        @Display(Display.Kind.DATETIME) OffsetDateTime endsAt,
        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(value = Display.Kind.ENUM, enumScope = "collection_commission_tier.status") String status,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        @Display(Display.Kind.DATETIME) Instant updatedAt
) {
    public static CollectionCommissionTierDto from(CollectionCommissionTier t) {
        return new CollectionCommissionTierDto(
                t.getUuid(), t.getName(), t.getDescription(), t.getBasis(), t.getMaxDays(), t.getMinAmount(),
                DisplayRefs.ref(t.getMinAmountCurrency()),
                t.getMinAmountCurrency() != null ? t.getMinAmountCurrency().getCode() : null,
                t.getCommissionPct(), t.getFlatAmount(),
                DisplayRefs.ref(t.getFlatAmountCurrency()),
                t.getFlatAmountCurrency() != null ? t.getFlatAmountCurrency().getCode() : null,
                t.getPromoterTypes().stream().map(DisplayRefs::ref).toList(),
                t.getAccrualPeriodStrategy(), t.getPartialSettlementPeriodStrategy(),
                t.getFinalSettlementPeriodStrategy(), t.getRetroactiveSettlementPeriodStrategy(),
                t.getAccrualPeriodAnchor(), t.getPartialSettlementPeriodAnchor(),
                t.getFinalSettlementPeriodAnchor(), t.getRetroactiveSettlementPeriodAnchor(),
                DisplayRefs.ref(t.getCampaign()), t.getStartsAt(), t.getEndsAt(),
                t.isActive(), t.getStatus(), t.getCreatedAt(), t.getUpdatedAt());
    }
}

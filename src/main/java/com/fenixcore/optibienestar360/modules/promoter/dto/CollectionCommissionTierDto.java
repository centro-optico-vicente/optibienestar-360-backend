package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.promoter.entity.CollectionCommissionTier;
import com.fenixcore.optibienestar360.modules.promoter.entity.CollectionCommissionTier.Basis;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Output DTO for the admin collection-commission-tier surface. Scalars carry a localized {@code _Display} sibling (ADR 0014). */
public record CollectionCommissionTierDto(
        UUID uuid,
        String name,
        String description,
        @Display(Display.Kind.ENUM) Basis basis,
        Integer maxDays,
        @Display(Display.Kind.NUMBER) BigDecimal maxAmount,
        @Display(Display.Kind.NUMBER) BigDecimal commissionPct,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "flatAmountCurrency_Code") BigDecimal flatAmount,
        @Display DisplayRef flatAmountCurrency,
        String flatAmountCurrency_Code,
        @Display DisplayRef promoterType,
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
                t.getUuid(), t.getName(), t.getDescription(), t.getBasis(), t.getMaxDays(), t.getMaxAmount(),
                t.getCommissionPct(), t.getFlatAmount(),
                DisplayRefs.ref(t.getFlatAmountCurrency()),
                t.getFlatAmountCurrency() != null ? t.getFlatAmountCurrency().getCode() : null,
                DisplayRefs.ref(t.getPromoterType()),
                DisplayRefs.ref(t.getCampaign()), t.getStartsAt(), t.getEndsAt(),
                t.isActive(), t.getStatus(), t.getCreatedAt(), t.getUpdatedAt());
    }
}

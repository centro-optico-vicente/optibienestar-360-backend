package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;
import com.fenixcore.optibienestar360.modules.promoter.entity.HierarchyOverrideTier;
import com.fenixcore.optibienestar360.modules.promoter.entity.HierarchyOverrideTier.OverrideCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Output DTO for the admin hierarchy-override-tier surface (V102/V108). Scalars carry a localized {@code _Display} sibling (ADR 0014). */
public record HierarchyOverrideTierDto(
        UUID uuid,
        String name,
        @Display DisplayRef rank,
        @Display(Display.Kind.ENUM) OverrideCategory category,
        int thresholdCount,
        @Display(Display.Kind.NUMBER) BigDecimal overridePct,
        @Display(value = Display.Kind.MONEY, moneyCurrencyField = "flatAmountCurrency_Code") BigDecimal flatAmount,
        @Display DisplayRef flatAmountCurrency,
        String flatAmountCurrency_Code,
        @Display(Display.Kind.ENUM) PeriodStrategy periodStrategy,
        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(value = Display.Kind.ENUM, enumScope = "hierarchy_override_tier.status") String status,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        @Display(Display.Kind.DATETIME) Instant updatedAt
) {
    public static HierarchyOverrideTierDto from(HierarchyOverrideTier t) {
        return new HierarchyOverrideTierDto(
                t.getUuid(), t.getName(), DisplayRefs.ref(t.getRank()), t.getCategory(),
                t.getThresholdCount(), t.getOverridePct(), t.getFlatAmount(),
                DisplayRefs.ref(t.getFlatAmountCurrency()),
                t.getFlatAmountCurrency() != null ? t.getFlatAmountCurrency().getCode() : null,
                t.getPeriodStrategy(), t.isActive(), t.getStatus(), t.getCreatedAt(), t.getUpdatedAt());
    }
}

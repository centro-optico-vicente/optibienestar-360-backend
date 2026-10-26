package com.fenixcore.optibienestar360.modules.ally.dto;

import com.fenixcore.optibienestar360.core.display.Display;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One ally's offer inside a public product detail ({@link PublicProductDetailDto})
 * — the "who offers it, at what price" comparison (hub ADR 0026). Carries the
 * offer's terms plus a compact slice of the ally's public identity so the
 * visitor can contact or open that ally straight from the comparison.
 */
public record PublicProductOfferDto(
        UUID offerUuid,
        @Display(Display.Kind.NUMBER) BigDecimal priceAmount,
        @Display(Display.Kind.NUMBER) BigDecimal discountPct,
        /** {@code priceAmount} with the discount applied — the comparison sorts by it. */
        @Display(Display.Kind.NUMBER) BigDecimal finalPrice,
        /** Where the preferential rate does NOT apply (V179). */
        String exclusions,

        // ─── Ally (public identity slice) ───
        UUID allyUuid,
        String allyName,
        List<String> allyTypeNames,
        String allyCityName,
        String allyLogoUrl,
        String allyPhone,
        String allyWhatsapp
) {}

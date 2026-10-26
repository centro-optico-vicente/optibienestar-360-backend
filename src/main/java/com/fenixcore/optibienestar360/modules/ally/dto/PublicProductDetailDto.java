package com.fenixcore.optibienestar360.modules.ally.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.modules.product.entity.Product.ProductType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Public detail of one catalog product ({@code GET /v1/public/products/{uuid}})
 * with every ally that offers it publicly and its own price and discount (hub
 * ADR 0026). {@code offers} are sorted by best final price first (offers
 * without a price last), then by higher discount, then by ally name. The
 * summary fields describe exactly that list (after the optional city filter).
 */
public record PublicProductDetailDto(
        UUID uuid,
        String name,
        String description,
        @Display(Display.Kind.ENUM) ProductType productType,
        String categoryName,
        @Display(Display.Kind.BOOLEAN) boolean requiresAppointment,
        String imageUrl,

        // ─── Summary ───
        int offersCount,
        /** Lowest final price among the offers that carry a price; {@code null} if none does. */
        @Display(Display.Kind.NUMBER) BigDecimal minFinalPrice,
        /** Highest discount among the offers; {@code null} if none has one. */
        @Display(Display.Kind.NUMBER) BigDecimal maxDiscountPct,

        List<PublicProductOfferDto> offers
) {}

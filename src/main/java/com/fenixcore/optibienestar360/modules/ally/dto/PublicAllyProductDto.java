package com.fenixcore.optibienestar360.modules.ally.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.modules.product.entity.Product.ProductType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Sanitized projection of an {@link com.fenixcore.optibienestar360.modules.ally.entity.AllyProduct}
 * for the public ally detail page. Only includes the fields a prospective
 * affiliate needs to evaluate the offering — no review workflow metadata
 * (status / reviewedBy / reviewedAt / reviewReason), no audit, no
 * publishedAt.
 *
 * <p>Filtering of which services are returned (only
 * {@code active AND published AND reviewStatus=APPROVED}) happens in the
 * mapper / service before this DTO is built; if you receive one in a
 * response, it's already cleared for public consumption.</p>
 */
public record PublicAllyProductDto(
        UUID uuid,
        /** The catalog product this offer is for — shared across allies when the product is general. */
        UUID productUuid,
        @Display(Display.Kind.ENUM) ProductType productType,
        String categoryName,
        String name,
        String description,
        @Display(Display.Kind.NUMBER) BigDecimal priceAmount,
        @Display(Display.Kind.NUMBER) BigDecimal discountPct,
        /** Where the preferential rate does NOT apply (V179) — affiliates must see it. */
        String exclusions,
        @Display(Display.Kind.BOOLEAN) boolean requiresAppointment,
        String imageUrl
) {}

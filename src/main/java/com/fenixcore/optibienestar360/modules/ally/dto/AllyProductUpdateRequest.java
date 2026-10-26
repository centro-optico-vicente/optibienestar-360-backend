package com.fenixcore.optibienestar360.modules.ally.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fenixcore.optibienestar360.modules.product.entity.Product.ProductType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload for {@code PUT /v1/admin/allies/{allyUuid}/products/{uuid}}.
 * PATCH semantics — non-null fields applied; null fields ignored.
 *
 * <p>The item fields ({@code name}, {@code productCategoryUuid}, …) edit the
 * product itself and are only accepted when it is exclusive to this ally; a
 * general product is shared and curated from {@code /v1/admin/products}.
 * {@code reviewStatus} is intentionally NOT here — workflow transitions go
 * through the dedicated approve / reject / remove endpoints.</p>
 */
public record AllyProductUpdateRequest(
        // ─── Exclusive product edits ───
        @JsonAlias("serviceCategoryUuid") UUID productCategoryUuid,
        ProductType productType,
        @Size(max = 200) String name,
        String description,
        Boolean requiresAppointment,

        // ─── Offer terms ───
        @Digits(integer = 8, fraction = 2)
        @DecimalMin(value = "0.0", inclusive = true)
        BigDecimal priceAmount,

        @Digits(integer = 3, fraction = 2)
        @DecimalMin(value = "0.0", inclusive = true)
        @DecimalMax(value = "100.0", inclusive = true)
        BigDecimal discountPct,

        /** Operational exclusions of the preferential rate, free text (V179). */
        String exclusions,

        Boolean published,
        Boolean active
) {}

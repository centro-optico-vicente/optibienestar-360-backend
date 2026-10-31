package com.fenixcore.optibienestar360.modules.ally.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyProduct.DiscountType;
import com.fenixcore.optibienestar360.modules.product.entity.Product.ProductType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload for {@code POST /v1/admin/allies/{allyUuid}/products} (hub ADR 0026).
 *
 * <p>Two ways to say <i>what</i> is offered:</p>
 * <ul>
 *   <li>{@code productUuid} — an existing product: a general one, or one
 *       already exclusive to this ally.</li>
 *   <li>no {@code productUuid} — the item fields ({@code productCategoryUuid},
 *       {@code name}, …) create a new product <b>exclusive</b> to this ally.
 *       {@code serviceCategoryUuid} is still accepted for the transition.</li>
 * </ul>
 *
 * <p>The offer lands at {@code review_status='PROPOSED'}; the admin moves it
 * to APPROVED through {@code /v1/admin/ally-products/{uuid}/approve} so the
 * review log stays consistent with proposals coming from the ally side.</p>
 */
public record AllyProductCreateRequest(
        UUID productUuid,

        // ─── New exclusive product (only when productUuid is null) ───
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
        // FLAT | PERCENTAGE (V210); null = PERCENTAGE. FLAT takes discountAmount, in the price currency.
        DiscountType discountType,
        @Digits(integer = 8, fraction = 2)
        @DecimalMin(value = "0.0", inclusive = false)
        BigDecimal discountAmount,

        /** Operational exclusions of the preferential rate, free text (V179). */
        String exclusions
) {}

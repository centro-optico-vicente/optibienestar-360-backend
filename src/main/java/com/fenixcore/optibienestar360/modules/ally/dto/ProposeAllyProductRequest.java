package com.fenixcore.optibienestar360.modules.ally.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fenixcore.optibienestar360.modules.product.entity.Product.ProductType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload for {@code POST /v1/me/allies/products} — the ally-side proposal
 * flow. Same shape as {@link AllyProductCreateRequest} (offer an existing
 * general product via {@code productUuid}, or propose a new exclusive one via
 * the item fields) plus {@code allyUuid} explicit in the body: a user can have
 * memberships in several allies, so the request must say which one acts.
 *
 * <p>The service validates an active OWNER or STAFF membership on the target
 * ally before persisting. The offer lands at {@code reviewStatus=PROPOSED};
 * admin moves it to APPROVED via the workflow endpoints, which logs the
 * actor + reason in {@code ally_product_review_log}.</p>
 */
public record ProposeAllyProductRequest(
        @NotNull UUID allyUuid,
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

        /** Operational exclusions of the preferential rate, free text (V179). */
        String exclusions
) {}

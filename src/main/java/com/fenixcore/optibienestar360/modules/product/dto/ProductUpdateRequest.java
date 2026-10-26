package com.fenixcore.optibienestar360.modules.product.dto;

import com.fenixcore.optibienestar360.modules.product.entity.Product.ProductType;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Payload for {@code PUT /v1/admin/products/{uuid}} — PATCH semantics,
 * non-null fields applied. {@code general = true} turns an exclusive product
 * into a general one (curation of backfilled duplicates); {@code ownerAllyUuid}
 * makes it exclusive, which the DB refuses while other allies still offer it.
 */
public record ProductUpdateRequest(
        @Size(max = 40) String code,
        @Size(max = 200) String name,
        String description,
        ProductType productType,
        UUID productCategoryUuid,
        UUID ownerAllyUuid,
        Boolean general,
        Boolean requiresAppointment,
        Boolean active
) {}

package com.fenixcore.optibienestar360.modules.product.dto;

import com.fenixcore.optibienestar360.modules.product.entity.Product.ProductType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Payload for {@code POST /v1/admin/products}. {@code ownerAllyUuid} omitted
 * creates a general product any ally may offer; set, the product is exclusive
 * to that ally (ADR 0026).
 */
public record ProductCreateRequest(
        @Size(max = 40) String code,
        @NotBlank @Size(max = 200) String name,
        String description,
        @NotNull ProductType productType,
        @NotNull UUID productCategoryUuid,
        UUID ownerAllyUuid,
        Boolean requiresAppointment,
        /** Listed in the general catalog; defaults to {@code true} (offers still need approval + their own publication). */
        Boolean published
) {}

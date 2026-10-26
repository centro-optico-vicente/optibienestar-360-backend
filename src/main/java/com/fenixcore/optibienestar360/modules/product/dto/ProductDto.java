package com.fenixcore.optibienestar360.modules.product.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.product.entity.Product.ProductType;

import java.time.Instant;
import java.util.UUID;

/**
 * Detail of a product master row ({@code GET /v1/admin/products/{uuid}}) —
 * what the edit form loads. See {@link ProductListItemDto} for the list row.
 */
public record ProductDto(
        UUID uuid,
        String code,
        String name,
        String description,
        @Display(Display.Kind.ENUM) ProductType productType,
        @Display DisplayRef productCategory,
        @Display DisplayRef ownerAlly,
        @Display(Display.Kind.BOOLEAN) boolean exclusive,
        @Display(Display.Kind.BOOLEAN) boolean requiresAppointment,

        /** Derived {@code publicBaseUrl + imageKey}; {@code null} until the image is explicitly published (spec §5). */
        String imageUrl,

        /** May appear in the public catalog (V199). */
        @Display(Display.Kind.BOOLEAN) boolean published,
        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(value = Display.Kind.ENUM, enumScope = "product.status") String status,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        @Display(Display.Kind.DATETIME) Instant updatedAt
) {}

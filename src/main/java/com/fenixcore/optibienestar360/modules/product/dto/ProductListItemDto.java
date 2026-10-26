package com.fenixcore.optibienestar360.modules.product.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.product.entity.Product.ProductType;

import java.time.Instant;
import java.util.UUID;

/**
 * Compact row of the admin product master list ({@code GET /v1/admin/products}).
 * {@code ownerAlly} is {@code null} for a general product; set, it names the
 * ally the product is exclusive to (ADR 0026). FK columns follow the
 * {@code _Display} convention (hub ADR 0014).
 */
public record ProductListItemDto(
        UUID uuid,
        String code,
        String name,
        @Display(Display.Kind.ENUM) ProductType productType,
        @Display DisplayRef productCategory,
        @Display DisplayRef ownerAlly,
        @Display(Display.Kind.BOOLEAN) boolean exclusive,
        @Display(Display.Kind.BOOLEAN) boolean requiresAppointment,
        String imageUrl,

        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(value = Display.Kind.ENUM, enumScope = "product.status") String status,
        @Display(Display.Kind.DATETIME) Instant createdAt
) {}

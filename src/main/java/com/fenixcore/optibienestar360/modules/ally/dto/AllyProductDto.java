package com.fenixcore.optibienestar360.modules.ally.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyProduct.ReviewStatus;
import com.fenixcore.optibienestar360.modules.catalog.dto.ProductCategoryDto;
import com.fenixcore.optibienestar360.modules.product.entity.Product.ProductType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Output DTO for an ally's offer of one catalog product (hub ADR 0026) —
 * {@code GET /v1/admin/allies/{allyUuid}/products[/{uuid}]}, the review queue
 * and the ally portal. Flattens the product's own fields ({@code name},
 * {@code productType}, {@code productCategory}…) next to the per-ally terms so
 * the frontend renders a row without follow-up calls; {@code productUuid} +
 * {@code exclusive} tell whether the item is shared (general) or the ally's own.
 *
 * <p>{@code reviewStatus} reflects the v2 workflow state; {@code published}
 * is orthogonal (admin controls visibility independently of approval).
 * Prices are in USD, so they use {@code NUMBER} rather than {@code MONEY}
 * (which formats as {@code VES}), matching {@code PublicAllyProductDto}.</p>
 */
public record AllyProductDto(
        UUID uuid,
        UUID allyUuid,

        // ─── Product (master) ───
        UUID productUuid,
        @Display(Display.Kind.ENUM) ProductType productType,
        @Display(Display.Kind.BOOLEAN) boolean exclusive,
        ProductCategoryDto productCategory,
        String name,
        String description,
        @Display(Display.Kind.BOOLEAN) boolean requiresAppointment,
        /** Derived {@code publicBaseUrl + imageKey} of the product; {@code null} until published (spec §5). */
        String imageUrl,

        // ─── Offer terms ───
        @Display(Display.Kind.NUMBER) BigDecimal priceAmount,
        @Display(Display.Kind.NUMBER) BigDecimal discountPct,
        /** Operational exclusions of the preferential rate (V179). */
        String exclusions,

        @Display(Display.Kind.ENUM) ReviewStatus reviewStatus,
        UUID reviewedByUuid,
        @Display(Display.Kind.DATETIME) Instant reviewedAt,
        String reviewReason,

        @Display(Display.Kind.BOOLEAN) boolean published,
        @Display(Display.Kind.DATETIME) Instant publishedAt,

        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(value = Display.Kind.ENUM, enumScope = "ally_product.status") String status,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        @Display(Display.Kind.DATETIME) Instant updatedAt
) {}

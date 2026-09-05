package com.fenixcore.optibienestar360.modules.ally.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyService.ReviewStatus;
import com.fenixcore.optibienestar360.modules.catalog.dto.ServiceCategoryDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Output DTO for {@code GET /v1/admin/allies/{allyUuid}/services} and
 * {@code GET /v1/admin/allies/{allyUuid}/services/{uuid}}. Wraps the
 * AllyService entity with a nested {@link ServiceCategoryDto} so the
 * frontend renders the category label without follow-up calls.
 *
 * <p>{@code reviewStatus} reflects the v2 workflow state — admin POSTs
 * normally land in PROPOSED and reach APPROVED via the dedicated workflow
 * endpoints; {@code published} is orthogonal (admin controls visibility
 * independently of approval).</p>
 *
 * <p>Scalars carry a localized {@code _Display} sibling (ADR 0014); prices
 * are in USD, so they use {@code NUMBER} rather than {@code MONEY} (which
 * formats as {@code VES}), matching {@code PublicAllyServiceDto}.</p>
 */
public record AllyServiceDto(
        UUID uuid,
        UUID allyUuid,
        ServiceCategoryDto serviceCategory,

        String name,
        String description,
        @Display(Display.Kind.NUMBER) BigDecimal priceAmount,
        @Display(Display.Kind.NUMBER) BigDecimal discountPct,
        @Display(Display.Kind.BOOLEAN) boolean requiresAppointment,

        @Display(Display.Kind.ENUM) ReviewStatus reviewStatus,
        UUID reviewedByUuid,
        @Display(Display.Kind.DATETIME) Instant reviewedAt,
        String reviewReason,

        @Display(Display.Kind.BOOLEAN) boolean published,
        @Display(Display.Kind.DATETIME) Instant publishedAt,

        /** Derived {@code publicBaseUrl + imageKey}; {@code null} until the image is explicitly published (spec §5). */
        String imageUrl,

        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(value = Display.Kind.ENUM, enumScope = "ally_service.status") String status,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        @Display(Display.Kind.DATETIME) Instant updatedAt
) {}

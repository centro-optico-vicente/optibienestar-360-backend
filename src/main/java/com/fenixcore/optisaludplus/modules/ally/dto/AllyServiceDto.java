package com.fenixcore.optisaludplus.modules.ally.dto;

import com.fenixcore.optisaludplus.modules.ally.entity.AllyService.ReviewStatus;
import com.fenixcore.optisaludplus.modules.catalog.dto.ServiceCategoryDto;

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
 */
public record AllyServiceDto(
        UUID uuid,
        UUID allyUuid,
        ServiceCategoryDto serviceCategory,

        String name,
        String description,
        BigDecimal priceUsd,
        BigDecimal discountPct,
        boolean requiresAppointment,

        ReviewStatus reviewStatus,
        UUID reviewedByUuid,
        Instant reviewedAt,
        String reviewReason,

        boolean published,
        Instant publishedAt,

        boolean active,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}

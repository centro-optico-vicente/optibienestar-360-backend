package com.fenixcore.optisaludplus.modules.ally.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Sanitized projection of an {@link com.fenixcore.optisaludplus.modules.ally.entity.AllyService}
 * for the public ally detail page. Only includes the fields a prospective
 * affiliate needs to evaluate the offering — no review workflow metadata
 * (status / reviewedBy / reviewedAt / reviewReason), no audit, no
 * publishedAt.
 *
 * <p>Filtering of which services are returned (only
 * {@code active AND published AND reviewStatus=APPROVED}) happens in the
 * mapper / service before this DTO is built; if you receive one in a
 * response, it's already cleared for public consumption.</p>
 */
public record PublicAllyServiceDto(
        UUID uuid,
        String categoryName,
        String name,
        String description,
        BigDecimal priceUsd,
        BigDecimal discountPct,
        boolean requiresAppointment
) {}

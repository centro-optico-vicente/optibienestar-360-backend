package com.fenixcore.optibienestar360.modules.ally.dto;

import com.fenixcore.optibienestar360.core.display.Display;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Sanitized projection of an {@link com.fenixcore.optibienestar360.modules.ally.entity.AllyService}
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
        @Display(Display.Kind.NUMBER) BigDecimal priceAmount,
        @Display(Display.Kind.NUMBER) BigDecimal discountPct,
        @Display(Display.Kind.BOOLEAN) boolean requiresAppointment
) {}

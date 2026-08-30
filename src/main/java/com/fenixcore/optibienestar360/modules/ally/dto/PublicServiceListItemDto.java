package com.fenixcore.optibienestar360.modules.ally.dto;

import com.fenixcore.optibienestar360.core.display.Display;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Sanitized projection for the <b>cross-ally</b> public service catalog
 * ({@code GET /v1/public/services}) — the "who offers X?" search. Unlike
 * {@link PublicAllyServiceDto} (used inside a single ally's context, where the
 * ally is already known), each row here carries a compact slice of its parent
 * ally's public identity so a search result is actionable on its own.
 *
 * <p>Only ever built from services that are {@code active AND published AND
 * reviewStatus=APPROVED} whose parent ally is itself {@code active AND
 * published} — an unpublished ally's offerings never leak here even if the
 * service row is published. Review-workflow metadata, audit and the ally's
 * internal fields (RIF, email) stay out; {@code allyUuid} lets the frontend
 * deep-link to the full ally detail for anything more.</p>
 */
public record PublicServiceListItemDto(
        // ─── Service ───
        UUID uuid,
        String categoryName,
        String name,
        String description,
        @Display(Display.Kind.NUMBER) BigDecimal priceUsd,
        @Display(Display.Kind.NUMBER) BigDecimal discountPct,
        @Display(Display.Kind.BOOLEAN) boolean requiresAppointment,
        String imageUrl,

        // ─── Parent ally (public identity slice) ───
        UUID allyUuid,
        String allyName,
        String allyTypeName,
        String allyCityName,
        String allyLogoUrl,
        String allyPhone
) {}

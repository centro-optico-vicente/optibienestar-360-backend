package com.fenixcore.optibienestar360.modules.ally.service;

import java.util.UUID;

/**
 * Filters of the public ally directory ({@code GET /v1/public/allies}), all
 * optional and combined with AND. Text fields take what a visitor types (see
 * {@link PublicCatalogSearch}); the UUID twins stay for clients holding ids.
 *
 * @param q        free text over the ally's name, RIF, ally number, description, city and state
 * @param city     city or state name
 * @param category allies offering something of this category (name or code)
 * @param type     allies offering {@code servicios} / {@code productos}
 * @param segment  {@code B2B} / {@code B2P} (the ally's own segment, V211)
 */
public record PublicAllySearch(
        String q,
        String city,
        UUID cityUuid,
        UUID professionUuid,
        String category,
        UUID categoryUuid,
        String type,
        String segment,
        /** Only allies with an opening-hours range covering "now" in the business zone (ADR 0031 §7). */
        boolean openNow,
        /** Only allies with at least one opening-hours range today in the business zone. */
        boolean openToday
) {
    public static PublicAllySearch none() {
        return new PublicAllySearch(null, null, null, null, null, null, null, null, false, false);
    }
}

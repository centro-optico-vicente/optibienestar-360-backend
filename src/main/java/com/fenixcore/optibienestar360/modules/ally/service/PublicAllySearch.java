package com.fenixcore.optibienestar360.modules.ally.service;

import java.util.List;
import java.util.UUID;

/**
 * Filters of the public ally directory ({@code GET /v1/public/allies}), all
 * optional and combined with AND. Text fields take what a visitor types (see
 * {@link PublicCatalogSearch}); the UUID twins stay for clients holding ids.
 *
 * <p>The list filters ({@code professionUuid}, {@code category},
 * {@code categoryUuid}, {@code allyType}, {@code allyTypeUuid}) take several
 * values — {@code ?category=a&category=b} — and keep allies matching ANY of
 * them (OR inside one filter, AND between filters). A missing or empty list
 * means "no filter".</p>
 *
 * @param q        free text over the ally's name, RIF, ally number, description, city and state
 * @param city     city or state name
 * @param category allies offering something of any of these categories (name or code)
 * @param allyType allies of any of these types — the ally's line of business, e.g. "Hotel" (name or code)
 * @param type     allies offering {@code servicios} / {@code productos}
 * @param segment  {@code B2B} / {@code B2P} (the ally's own segment, V211)
 */
public record PublicAllySearch(
        String q,
        String city,
        UUID cityUuid,
        List<UUID> professionUuid,
        List<String> category,
        List<UUID> categoryUuid,
        List<String> allyType,
        List<UUID> allyTypeUuid,
        String type,
        String segment,
        /** Only allies with an opening-hours range covering "now" in the business zone (ADR 0031 §7). */
        boolean openNow,
        /** Only allies with at least one opening-hours range today in the business zone. */
        boolean openToday
) {
    public PublicAllySearch {
        professionUuid = PublicCatalogSearch.uuids(professionUuid);
        category = PublicCatalogSearch.texts(category);
        categoryUuid = PublicCatalogSearch.uuids(categoryUuid);
        allyType = PublicCatalogSearch.texts(allyType);
        allyTypeUuid = PublicCatalogSearch.uuids(allyTypeUuid);
    }

    public static PublicAllySearch none() {
        return new PublicAllySearch(null, null, null, null, null, null, null, null, null, null, false, false);
    }
}

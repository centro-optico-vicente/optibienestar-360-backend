package com.fenixcore.optibienestar360.modules.ally.service;

import java.util.List;
import java.util.UUID;

/**
 * Filters of the public catalog ({@code GET /v1/public/products}), all optional
 * and combined with AND (hub ADR 0026). The text fields are what a visitor
 * types — see {@link PublicCatalogSearch}; the UUID twins stay for clients that
 * already hold an id (the SPA, external integrations).
 *
 * <p>{@code category}, {@code categoryUuid}, {@code ally} and {@code allyUuid}
 * take several values — {@code ?category=a&category=b} — and keep offers
 * matching ANY of them (OR inside one filter, AND between filters). A missing
 * or empty list means "no filter".</p>
 *
 * @param q        free text over product, category, ally, city and state
 * @param type     {@code servicio} / {@code producto} (or {@code SERVICE} / {@code PRODUCT})
 * @param category category names or codes
 * @param city     the ally's city or state name
 * @param ally     allies' names, RIFs or ally numbers
 */
public record PublicProductSearch(
        String q,
        String type,
        List<String> category,
        List<UUID> categoryUuid,
        String city,
        UUID cityUuid,
        List<String> ally,
        List<UUID> allyUuid
) {
    public PublicProductSearch {
        category = PublicCatalogSearch.texts(category);
        categoryUuid = PublicCatalogSearch.uuids(categoryUuid);
        ally = PublicCatalogSearch.texts(ally);
        allyUuid = PublicCatalogSearch.uuids(allyUuid);
    }

    public static PublicProductSearch none() {
        return new PublicProductSearch(null, null, null, null, null, null, null, null);
    }
}

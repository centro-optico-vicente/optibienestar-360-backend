package com.fenixcore.optibienestar360.modules.ally.service;

import java.util.UUID;

/**
 * Filters of the public catalog ({@code GET /v1/public/products}), all optional
 * and combined with AND (hub ADR 0026). The text fields are what a visitor
 * types — see {@link PublicCatalogSearch}; the UUID twins stay for clients that
 * already hold an id (the SPA, external integrations).
 *
 * @param q        free text over product, category, ally, city and state
 * @param type     {@code servicio} / {@code producto} (or {@code SERVICE} / {@code PRODUCT})
 * @param category category name or code
 * @param city     the ally's city or state name
 * @param ally     the ally's name, RIF or ally number
 */
public record PublicProductSearch(
        String q,
        String type,
        String category,
        UUID categoryUuid,
        String city,
        UUID cityUuid,
        String ally,
        UUID allyUuid
) {
    public static PublicProductSearch none() {
        return new PublicProductSearch(null, null, null, null, null, null, null, null);
    }
}

package com.fenixcore.optisaludplus.core.util;

import org.springframework.data.domain.Pageable;

/**
 * Small helpers for paginated-list endpoints. Currently exposes the
 * "dropdown case" check used by cached read-paths: when the caller asks
 * for everything (unpaged) with no filter, no free-text search and no
 * extra parent filters, the response is a stable, cacheable snapshot.
 *
 * <p>Other combinations (paged, RSQL filter, q, parent filter) skip the
 * cache and go straight to the DB — see {@code SearchSpecifications}.</p>
 */
public final class ListQuery {

    private ListQuery() {}

    /**
     * @return true when the request is {@link Pageable#unpaged() unpaged},
     *         carries no RSQL {@code filter} and no {@code q} search term.
     *         The cacheable "load everything for a dropdown" case.
     */
    public static boolean isUnfilteredUnpaged(Pageable pageable, String filter, String q) {
        return pageable.isUnpaged()
                && (filter == null || filter.isBlank())
                && (q == null || q.isBlank());
    }

    /**
     * Same as {@link #isUnfilteredUnpaged(Pageable, String, String)} but also
     * requires every {@code extraFilter} to be null/blank — for services that
     * accept additional parent filters (e.g. State's {@code countryIsoCode},
     * City's {@code stateUuid}/{@code stateCode}).
     */
    public static boolean isUnfilteredUnpaged(Pageable pageable, String filter, String q, Object... extraFilters) {
        if (!isUnfilteredUnpaged(pageable, filter, q)) {
            return false;
        }
        for (Object ef : extraFilters) {
            if (ef instanceof String s) {
                if (!s.isBlank()) return false;
            } else if (ef != null) {
                return false;
            }
        }
        return true;
    }
}

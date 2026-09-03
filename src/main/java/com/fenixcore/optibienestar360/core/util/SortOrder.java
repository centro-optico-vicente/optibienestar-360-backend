package com.fenixcore.optibienestar360.core.util;

import java.util.Locale;

/**
 * One field+direction pair of a (possibly multi-column) configured default
 * sort — shared shape between {@code EntityConfig.defaultSort} (per-entity,
 * validated against that entity's own {@code SortFieldValidator} map) and
 * {@code SystemConfig.defaultSort} (global fallback, validated against
 * {@link CommonSortFields}). {@code field} is the key the frontend table
 * sends, not necessarily a raw JPA path — same convention as
 * {@code SortFieldValidator.SORTABLE_FIELDS}. {@code direction} is
 * {@code "ASC"} or {@code "DESC"}.
 */
public record SortOrder(String field, String direction) {

    public static final String DEFAULT_FIELD = "createdAt";
    public static final String DEFAULT_DIRECTION = "DESC";

    /** The fallback every list endpoint uses when neither the entity nor the
     *  system has a configured default sort — see {@link DefaultSortResolver}. */
    public static final SortOrder DEFAULT = new SortOrder(DEFAULT_FIELD, DEFAULT_DIRECTION);

    public SortOrder {
        // Only normalizes casing — never invents a direction when null. Text/numeric
        // fields conventionally default ASC, dates conventionally default DESC; this
        // record has no way to know which kind `field` is, so guessing here would
        // silently apply the wrong convention to a non-date field. Only DEFAULT
        // (createdAt) is explicitly DESC.
        direction = direction == null ? null : direction.toUpperCase(Locale.ROOT);
    }

    /** Equivalent to {@link #DEFAULT} — lets callers write {@code new SortOrder()}. */
    public SortOrder() {
        this(DEFAULT_FIELD, DEFAULT_DIRECTION);
    }
}

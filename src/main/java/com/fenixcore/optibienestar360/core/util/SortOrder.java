package com.fenixcore.optibienestar360.core.util;

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
public record SortOrder(String field, String direction) {}

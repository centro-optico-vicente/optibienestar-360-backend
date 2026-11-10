package com.fenixcore.optibienestar360.core.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * In-memory twin of {@link SearchSpecifications} for the small, unpaged
 * sub-lists (a member's documents, a role's users…) that are loaded whole and
 * filtered in Java. Same semantics as the SQL path: case-insensitive and
 * accent-insensitive substring match, so {@code jose} matches {@code José}.
 */
public final class TextSearch {

    private TextSearch() {}

    /** Lower-cases and strips diacritics; {@code null} becomes an empty string. */
    public static String fold(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }

    /**
     * A predicate that is true when any of the values extracted from the item
     * contains {@code q}. A null/blank {@code q} matches everything, so it is
     * safe to apply unconditionally.
     */
    @SafeVarargs
    public static <T> Predicate<T> matching(String q, java.util.function.Function<T, String>... extractors) {
        if (q == null || q.isBlank()) {
            return item -> true;
        }
        String needle = fold(q.trim());
        return item -> {
            for (var extractor : extractors) {
                if (fold(extractor.apply(item)).contains(needle)) {
                    return true;
                }
            }
            return false;
        };
    }
}

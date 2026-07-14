package com.fenixcore.optibienestar360.core.util;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

/**
 * Builders for {@code ?q=…} free-text search Specifications. Backed by Postgres
 * {@code unaccent(lower(field))} so the search is both case-insensitive and
 * accent-insensitive — e.g. typing {@code merida} matches {@code Mérida}.
 *
 * <p>The {@code unaccent} extension is created in {@code V1__initial_extensions.sql}
 * so the function is always available on the {@code public} schema.</p>
 */
public final class SearchSpecifications {

    private SearchSpecifications() {}

    /**
     * Returns a Specification that ORs unaccented case-insensitive LIKE matches
     * across the given string fields. The query is wrapped with {@code %…%} so
     * any substring matches.
     *
     * <p>If {@code q} is null or blank the returned Specification is a no-op
     * ({@code AND TRUE}) — safe to chain unconditionally with other filters.</p>
     */
    public static <T> Specification<T> acrossFields(String q, String... fields) {
        if (q == null || q.isBlank() || fields.length == 0) {
            return (root, query, cb) -> cb.conjunction();
        }
        String normalized = "%" + q.trim().toLowerCase() + "%";
        return (root, query, cb) -> {
            Expression<String> pattern = cb.function(
                    "unaccent", String.class, cb.literal(normalized));
            Predicate[] preds = new Predicate[fields.length];
            for (int i = 0; i < fields.length; i++) {
                Expression<String> field = cb.function(
                        "unaccent", String.class,
                        cb.lower(root.get(fields[i]).as(String.class)));
                preds[i] = cb.like(field, pattern);
            }
            return cb.or(preds);
        };
    }
}

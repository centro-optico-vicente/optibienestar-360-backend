package com.fenixcore.optibienestar360.core.util;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

/**
 * Builders for {@code ?q=…} free-text search Specifications. Backed by Postgres
 * {@code immutable_unaccent(lower(field))} so the search is both case-insensitive
 * and accent-insensitive — e.g. typing {@code merida} matches {@code Mérida}.
 *
 * <p><b>Why {@code immutable_unaccent} and not plain {@code unaccent}:</b> the
 * trigram indexes are built on that exact expression
 * ({@code idx_persons_full_name_unaccent} in V15,
 * {@code idx_allies_name_unaccent} in V11), and Postgres only matches an
 * expression index when the predicate uses the <i>same</i> expression. Calling
 * the raw {@code unaccent()} here produced a different expression, so the GIN
 * indexes were never used and every {@code ?q=} search fell back to a Seq Scan
 * with a per-row function call. (The wrapper exists because {@code unaccent()}
 * is STABLE and therefore not indexable — see V11.) Being IMMUTABLE also lets
 * the planner fold the pattern argument to a constant instead of re-evaluating
 * it per row.</p>
 *
 * <p>The {@code unaccent} extension is created in {@code V1__initial_extensions.sql}
 * and the {@code immutable_unaccent} wrapper in {@code V11__allies.sql}.</p>
 *
 * <p><b>Limitación conocida:</b> cuando se buscan varios campos a la vez, el
 * planner necesita un índice en <i>cada</i> rama del OR para armar un BitmapOr;
 * si alguno no lo tiene (hoy solo {@code persons.full_name} y {@code allies.name}
 * tienen índice trigram), cae igual a Seq Scan. Esta clase deja la expresión
 * lista para que el índice se use; cubrir el resto de los campos es una decisión
 * de indexado aparte.</p>
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
                    "immutable_unaccent", String.class, cb.literal(normalized));
            Predicate[] preds = new Predicate[fields.length];
            for (int i = 0; i < fields.length; i++) {
                Expression<String> field = cb.function(
                        "immutable_unaccent", String.class,
                        cb.lower(resolvePath(root, fields[i]).as(String.class)));
                preds[i] = cb.like(field, pattern);
            }
            return cb.or(preds);
        };
    }

    /**
     * Resolves a possibly dotted field name (e.g. {@code "person.fullName"})
     * into a {@link Path} by chaining {@code get(...)} calls, since
     * {@link Path#get(String)} only resolves a single attribute and does not
     * split on {@code .} itself.
     */
    private static Path<?> resolvePath(Path<?> root, String field) {
        Path<?> path = root;
        for (String segment : field.split("\\.")) {
            path = path.get(segment);
        }
        return path;
    }
}

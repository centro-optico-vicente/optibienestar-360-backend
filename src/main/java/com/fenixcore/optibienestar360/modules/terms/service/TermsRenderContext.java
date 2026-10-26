package com.fenixcore.optibienestar360.modules.terms.service;

import java.util.List;
import java.util.Map;

/**
 * Per-entity input of {@link TermsTemplateRenderer}: the scalar values of a
 * template ({@code {{ally.name}}} → {@code "Clínica Ejemplo, C.A."}) plus the
 * rows of its repeated sections ({@code {{#agreement.rates}}…{{/agreement.rates}}}
 * → one map of row fields per row, see {@link TermsVariables.Section}).
 *
 * <p>A missing or blank value renders as its pending note; a missing or
 * empty section renders its section pending note once.</p>
 *
 * @param values   scalar values by variable key (never {@code null})
 * @param sections rows by section key, in display order (never {@code null})
 */
public record TermsRenderContext(Map<String, String> values, Map<String, List<Map<String, String>>> sections) {

    /** No per-entity data: every per-entity value and section renders its pending note. */
    public static final TermsRenderContext EMPTY = new TermsRenderContext(Map.of(), Map.of());

    public TermsRenderContext {
        values = values == null ? Map.of() : values;
        sections = sections == null ? Map.of() : sections;
    }

    /** Only scalar values, no section rows. */
    public static TermsRenderContext of(Map<String, String> values) {
        return new TermsRenderContext(values, Map.of());
    }

    /** The rows of section {@code key}; empty when the section has none. */
    public List<Map<String, String>> rows(String key) {
        List<Map<String, String>> rows = sections.get(key);
        return rows == null ? List.of() : rows;
    }
}

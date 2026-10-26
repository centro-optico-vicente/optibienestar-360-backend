package com.fenixcore.optibienestar360.modules.terms.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fenixcore.optibienestar360.modules.terms.service.TermsVariables;

import java.util.List;

/**
 * One variable a T&C template of a given kind may use —
 * {@code GET /v1/admin/terms/variables?type=}. {@code key} is what goes
 * between the double braces ({@code {{member.fullName}}}); {@code description}
 * is localized by the request locale. {@code kind} tells how the value is
 * formatted in the contract ({@code TEXT}, {@code DATE}, {@code NUMBER},
 * {@code MONEY}, {@code BOOLEAN}) or, for {@code LIST}, that it is a repeated
 * section written as a block {@code {{#key}}…{{/key}}} whose row placeholders
 * are the single-word {@code fields} ({@code {{serviceName}}}); {@code fields}
 * is omitted for every other kind.
 */
public record TermsVariableDto(
        String key,
        String description,
        TermsVariables.Kind kind,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<TermsVariableDto> fields
) {

    /** A scalar variable (no row fields). */
    public TermsVariableDto(String key, String description, TermsVariables.Kind kind) {
        this(key, description, kind, null);
    }
}

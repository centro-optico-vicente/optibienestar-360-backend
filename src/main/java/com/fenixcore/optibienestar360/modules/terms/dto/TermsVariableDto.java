package com.fenixcore.optibienestar360.modules.terms.dto;

import com.fenixcore.optibienestar360.modules.terms.service.TermsVariables;

/**
 * One variable a T&C template of a given kind may use —
 * {@code GET /v1/admin/terms/variables?type=}. {@code key} is what goes
 * between the double braces ({@code {{member.fullName}}}); {@code description}
 * is localized by the request locale. {@code kind} tells how the value is
 * formatted in the contract ({@code TEXT}, {@code DATE}, {@code NUMBER},
 * {@code MONEY}, {@code BOOLEAN}).
 */
public record TermsVariableDto(
        String key,
        String description,
        TermsVariables.Kind kind
) {}

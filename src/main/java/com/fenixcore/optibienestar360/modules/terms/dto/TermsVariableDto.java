package com.fenixcore.optibienestar360.modules.terms.dto;

/**
 * One variable a T&C template of a given kind may use —
 * {@code GET /v1/admin/terms/variables?type=}. {@code key} is what goes
 * between the double braces ({@code {{member.fullName}}}); {@code description}
 * is localized by the request locale.
 */
public record TermsVariableDto(
        String key,
        String description
) {}

package com.fenixcore.optibienestar360.modules.terms.service;

import com.fenixcore.optibienestar360.modules.membership.entity.Plan;

import java.time.LocalDate;

/**
 * The applicant data a MEMBER contract is rendered with before the member
 * exists ({@link TermsVariableResolver#forApplicant}). Any part may be
 * {@code null} — e.g. while the public form is still being filled — and
 * renders as its pending note.
 */
public record MemberTermsApplicant(
        String firstName,
        String lastName,
        String documentType,
        String documentNumber,
        LocalDate birthDate,
        Plan plan
) {

    /** {@code "first last"} with blanks collapsed, the {@code persons.full_name} rule; {@code null} when both are blank. */
    public String fullName() {
        String joined = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName))
                .trim().replaceAll("\\s+", " ");
        return joined.isEmpty() ? null : joined;
    }
}

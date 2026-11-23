package com.fenixcore.optibienestar360.modules.organization.entity;

/**
 * Which fee types a <b>manual</b> subsidy may discount in this organization
 * ({@code organizations.subsidy_mode}, V262, hub ADR 0041 §1). Referral-reward
 * subsidies are not governed by the mode — they always apply.
 */
public enum SubsidyMode {
    /** No manual subsidy discounts anything, and none can be created or edited. */
    DISABLED,
    /** Only the monthly percentage discounts. */
    MONTHLY,
    /** Only the inscription percentage discounts. */
    INSCRIPTION,
    /** Both percentages discount. */
    MONTHLY_AND_INSCRIPTION;

    public boolean monthlyEnabled() {
        return this == MONTHLY || this == MONTHLY_AND_INSCRIPTION;
    }

    public boolean inscriptionEnabled() {
        return this == INSCRIPTION || this == MONTHLY_AND_INSCRIPTION;
    }

    /** Lenient parse for request payloads: {@code null}/blank = {@code null}; unknown value = 422. */
    public static SubsidyMode parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("organization.subsidy_mode.invalid");
        }
    }
}

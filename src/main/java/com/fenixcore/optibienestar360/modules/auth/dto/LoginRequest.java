package com.fenixcore.optibienestar360.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Login credentials. {@code identifier} is the email or the national ID
 * ({@code V12345678}, {@code 12345678}); {@code email} is the legacy field,
 * still accepted as an alias so older clients keep working.
 */
public record LoginRequest(
        String identifier,
        String email,
        @NotBlank String password
) {

    /** The value to look the account up by: {@code identifier}, else the legacy {@code email}. */
    public String resolvedIdentifier() {
        if (identifier != null && !identifier.isBlank()) {
            return identifier.trim();
        }
        return email == null ? "" : email.trim();
    }
}

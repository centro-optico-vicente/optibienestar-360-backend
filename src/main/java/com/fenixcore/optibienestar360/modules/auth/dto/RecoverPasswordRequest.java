package com.fenixcore.optibienestar360.modules.auth.dto;

/**
 * Password recovery by email or national ID. {@code email} is the legacy
 * field, still accepted as an alias of {@code identifier}.
 */
public record RecoverPasswordRequest(
        String identifier,
        String email
) {

    public String resolvedIdentifier() {
        if (identifier != null && !identifier.isBlank()) {
            return identifier.trim();
        }
        return email == null ? "" : email.trim();
    }
}

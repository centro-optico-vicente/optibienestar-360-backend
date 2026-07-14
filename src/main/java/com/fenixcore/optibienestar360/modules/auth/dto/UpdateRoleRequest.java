package com.fenixcore.optibienestar360.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload to update a role's display attributes. {@code name} can be renamed
 * (uniqueness re-validated against other roles, excluding self), but rename
 * is risky because {@code @PreAuthorize} doesn't reference role names —
 * permissions do — so a rename is purely a label change as long as
 * {@code findByName(...)} callers don't have the old name cached.
 *
 * <p>The {@code SYSTEM} role is rejected by the service guard.</p>
 */
public record UpdateRoleRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,49}$",
                 message = "{validation.role.name.format}")
        String name,

        @Size(max = 200) String description
) {}

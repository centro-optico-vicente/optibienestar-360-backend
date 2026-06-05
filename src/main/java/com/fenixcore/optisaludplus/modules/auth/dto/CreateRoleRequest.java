package com.fenixcore.optisaludplus.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload to create a role. {@code name} is the natural key for role lookups
 * (used by {@code findByName} and referenced by string in {@code @PreAuthorize}
 * indirectly via permission grants), so the format is constrained to the same
 * UPPER_SNAKE_CASE convention as the existing V6 seed (SYSTEM, ADMINISTRADOR,
 * OPERADOR_MEDICO, etc.). Uniqueness is enforced at three layers: this regex,
 * the {@code RoleService.create} check (clean 422 on duplicate), and the
 * {@code roles.name UNIQUE} constraint in V5 as last-resort safety net.
 */
public record CreateRoleRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,49}$",
                 message = "{validation.role.name.format}")
        String name,

        @Size(max = 200) String description
) {}

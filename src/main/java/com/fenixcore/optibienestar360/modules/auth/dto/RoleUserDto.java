package com.fenixcore.optibienestar360.modules.auth.dto;

import com.fenixcore.optibienestar360.core.display.Display;

import java.util.UUID;

/**
 * Compact projection of a {@code User} for the membership listing under
 * {@code /v1/admin/roles/{roleUuid}/users}. Mirrors
 * {@link com.fenixcore.optibienestar360.modules.ally.dto.AllyUserDto}'s
 * flattening approach: enough to render a table row without a second
 * round-trip, without pulling in the full {@link UserDto} shape.
 * {@code status} / {@code active} carry a localized {@code _Display}
 * sibling (hub ADR 0014).
 */
public record RoleUserDto(
        UUID userUuid,
        String email,
        String fullName,
        @Display(value = Display.Kind.ENUM, enumScope = "user.status") String status,
        @Display(Display.Kind.BOOLEAN) boolean active
) {}

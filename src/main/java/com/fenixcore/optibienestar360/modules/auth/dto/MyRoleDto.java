package com.fenixcore.optibienestar360.modules.auth.dto;

import java.util.UUID;

/** One role selectable as the caller's active session role — {@code GET /v1/me/roles}. */
public record MyRoleDto(
        UUID uuid,
        String name,
        String description,
        boolean isDefault
) {}

package com.fenixcore.optibienestar360.modules.auth.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Body of {@code POST /v1/admin/roles/{roleUuid}/users} — assign a user to a role. */
public record AssignRoleUserRequest(
        @NotNull(message = "{validation.user_uuid.required}") UUID userUuid
) {}

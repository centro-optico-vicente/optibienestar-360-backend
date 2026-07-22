package com.fenixcore.optibienestar360.modules.auth.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Replace-the-set semantics: the role ends up with exactly these permission
 * UUIDs. An empty list strips all permissions; null is rejected by validation.
 */
public record UpdateRolePermissionsRequest(
        @NotNull(message = "{validation.permission_uuids.required}") List<UUID> permissionUuids
) {}

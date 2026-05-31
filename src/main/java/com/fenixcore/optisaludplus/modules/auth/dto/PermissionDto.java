package com.fenixcore.optisaludplus.modules.auth.dto;

import java.util.UUID;

/**
 * Technical permission identifiers (e.g. {@code MEMBER_CREATE}) are never
 * exposed — admins identify permissions by {@code uuid} and see the Spanish
 * {@code name} (short, for the checkbox) and optional longer
 * {@code description} (for tooltip / help text).
 */
public record PermissionDto(
        UUID uuid,
        String name,
        String description
) {}

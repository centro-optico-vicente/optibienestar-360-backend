package com.fenixcore.optibienestar360.modules.auth.dto;

import java.util.UUID;

/**
 * {@code code} is the technical permission key (e.g. {@code MEMBER_CREATE}) —
 * exposed so the admin UI can group/bulk-toggle permissions by action suffix
 * (_CREATE, _DELETE, _VIEW, ...). {@code name} is the Spanish label for the
 * checkbox and {@code description} an optional longer help text.
 */
public record PermissionDto(
        UUID uuid,
        String code,
        String name,
        String description
) {}

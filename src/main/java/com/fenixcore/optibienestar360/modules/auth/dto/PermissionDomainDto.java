package com.fenixcore.optibienestar360.modules.auth.dto;

import java.util.List;
import java.util.UUID;

/**
 * Pre-sorted by {@code displayOrder} (domain) and {@code name}
 * (permission) — frontend just iterates and renders.
 */
public record PermissionDomainDto(
        UUID uuid,
        String code,
        String name,
        String icon,
        String description,
        int displayOrder,
        List<PermissionDto> permissions
) {}

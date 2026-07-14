package com.fenixcore.optibienestar360.modules.auth.dto;

import java.util.UUID;

public record RoleDto(
        UUID uuid,
        String name,
        String description
) {}

package com.fenixcore.optisaludplus.modules.auth.dto;

import java.util.UUID;

public record RoleDto(
        UUID uuid,
        String name,
        String description
) {}

package com.fenixcore.optisaludplus.modules.catalog.dto;

import java.util.UUID;

public record AllyTypeDto(
        UUID uuid,
        String code,
        String name,
        String description,
        boolean active
) {}

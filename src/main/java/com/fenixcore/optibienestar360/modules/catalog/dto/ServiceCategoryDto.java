package com.fenixcore.optibienestar360.modules.catalog.dto;

import java.util.UUID;

public record ServiceCategoryDto(
        UUID uuid,
        String code,
        String name,
        String description,
        boolean active
) {}

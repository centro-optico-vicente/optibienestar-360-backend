package com.fenixcore.optibienestar360.modules.catalog.dto;

import java.util.UUID;

public record OccupationDto(
        UUID uuid,
        String name,
        String description,
        boolean active
) {}

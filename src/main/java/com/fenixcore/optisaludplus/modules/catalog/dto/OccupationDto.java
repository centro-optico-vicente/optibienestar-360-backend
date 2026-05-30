package com.fenixcore.optisaludplus.modules.catalog.dto;

import java.util.UUID;

public record OccupationDto(
        UUID uuid,
        String name,
        String description,
        boolean active
) {}

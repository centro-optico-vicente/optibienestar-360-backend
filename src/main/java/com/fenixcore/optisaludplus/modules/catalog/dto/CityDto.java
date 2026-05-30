package com.fenixcore.optisaludplus.modules.catalog.dto;

import java.util.UUID;

public record CityDto(
        UUID uuid,
        String name,
        UUID stateUuid,
        String stateCode,
        boolean active
) {}

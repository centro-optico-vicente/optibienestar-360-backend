package com.fenixcore.optisaludplus.modules.catalog.dto;

import java.util.UUID;

public record StateDto(
        UUID uuid,
        String code,
        String name,
        UUID countryUuid,
        String countryIsoCode,
        boolean active
) {}

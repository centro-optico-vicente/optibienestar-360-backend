package com.fenixcore.optisaludplus.modules.catalog.dto;

import java.util.UUID;

public record CountryDto(
        UUID uuid,
        String isoCode,
        String name,
        boolean active
) {}

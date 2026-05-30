package com.fenixcore.optisaludplus.modules.catalog.dto;

import java.util.UUID;

public record GenderDto(
        UUID uuid,
        String code,
        String name,
        boolean active
) {}

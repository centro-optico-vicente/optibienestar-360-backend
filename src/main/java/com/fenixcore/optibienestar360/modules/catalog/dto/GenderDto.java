package com.fenixcore.optibienestar360.modules.catalog.dto;

import java.util.UUID;

public record GenderDto(
        UUID uuid,
        String code,
        String name,
        boolean active
) {}

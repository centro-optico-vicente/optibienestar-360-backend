package com.fenixcore.optisaludplus.modules.catalog.dto;

import java.util.UUID;

public record MedicalSpecialtyDto(
        UUID uuid,
        String code,
        String name,
        String description,
        boolean active
) {}

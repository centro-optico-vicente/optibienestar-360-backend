package com.fenixcore.optisaludplus.modules.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OccupationUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 200) String description
) {}

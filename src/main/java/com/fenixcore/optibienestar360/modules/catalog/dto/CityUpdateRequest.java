package com.fenixcore.optibienestar360.modules.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CityUpdateRequest(
        @NotBlank @Size(max = 120) String name
) {}

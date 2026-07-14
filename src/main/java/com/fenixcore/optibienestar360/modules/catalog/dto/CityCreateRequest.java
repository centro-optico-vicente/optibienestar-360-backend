package com.fenixcore.optibienestar360.modules.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CityCreateRequest(
        @NotNull UUID stateUuid,
        @NotBlank @Size(max = 120) String name
) {}

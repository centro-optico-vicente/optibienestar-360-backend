package com.fenixcore.optibienestar360.modules.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record StateCreateRequest(
        @NotNull UUID countryUuid,
        @NotBlank @Size(max = 10) String code,
        @NotBlank @Size(max = 100) String name
) {}

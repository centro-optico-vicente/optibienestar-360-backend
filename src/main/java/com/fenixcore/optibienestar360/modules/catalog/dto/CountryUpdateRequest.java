package com.fenixcore.optibienestar360.modules.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CountryUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        Boolean active,
        UUID officialCurrencyUuid
) {}

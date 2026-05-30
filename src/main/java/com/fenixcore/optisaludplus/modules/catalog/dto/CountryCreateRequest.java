package com.fenixcore.optisaludplus.modules.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CountryCreateRequest(
        @NotBlank @Pattern(regexp = "^[A-Z]{2}$", message = "isoCode must be 2 uppercase letters (ISO 3166-1 alpha-2)") String isoCode,
        @NotBlank @Size(max = 100) String name
) {}

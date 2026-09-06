package com.fenixcore.optibienestar360.modules.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CountryCreateRequest(
        @NotBlank @Pattern(regexp = "^[A-Z]{2}$", message = "{validation.iso_code.alpha2}") String isoCode,
        @NotBlank @Size(max = 100) String name,
        UUID officialCurrencyUuid
) {}

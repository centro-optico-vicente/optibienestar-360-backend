package com.fenixcore.optibienestar360.modules.currency.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CurrencyCreateRequest(
        @NotBlank @Pattern(regexp = "^[A-Z]{3,4}$", message = "{validation.currency.code}") String code,
        @NotBlank @Size(max = 60) String name,
        @NotBlank @Size(max = 6) String symbol,
        @NotNull @Min(0) Short decimalPlaces
) {}

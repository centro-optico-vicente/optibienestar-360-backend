package com.fenixcore.optibienestar360.modules.currency.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code code} is the immutable natural key — not editable here, same as {@code ScheduledJob.code}. */
public record CurrencyUpdateRequest(
        @NotBlank @Size(max = 60) String name,
        @NotBlank @Size(max = 6) String symbol,
        @Min(0) Short decimalPlaces,
        Boolean active
) {}

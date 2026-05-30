package com.fenixcore.optisaludplus.modules.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GenderCreateRequest(
        @NotBlank @Pattern(regexp = "^[A-Z]$", message = "code must be a single uppercase letter") String code,
        @NotBlank @Size(max = 20) String name
) {}

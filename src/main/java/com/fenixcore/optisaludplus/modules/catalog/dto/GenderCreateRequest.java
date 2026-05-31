package com.fenixcore.optisaludplus.modules.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GenderCreateRequest(
        @NotBlank @Pattern(regexp = "^[A-Z]$", message = "{validation.code.uppercase.single}") String code,
        @NotBlank @Size(max = 20) String name
) {}

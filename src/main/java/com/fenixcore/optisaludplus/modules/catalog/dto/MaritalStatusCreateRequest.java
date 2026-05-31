package com.fenixcore.optisaludplus.modules.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MaritalStatusCreateRequest(
        @NotBlank @Pattern(regexp = "^[A-Z_]{1,20}$", message = "{validation.code.uppercase.medium}") String code,
        @NotBlank @Size(max = 50) String name
) {}

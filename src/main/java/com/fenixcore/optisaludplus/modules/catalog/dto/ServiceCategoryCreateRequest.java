package com.fenixcore.optisaludplus.modules.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ServiceCategoryCreateRequest(
        @NotBlank @Pattern(regexp = "^[A-Z_]{1,40}$", message = "{validation.code.uppercase.long}") String code,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 200) String description
) {}

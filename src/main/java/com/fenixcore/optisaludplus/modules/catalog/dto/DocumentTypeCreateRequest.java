package com.fenixcore.optisaludplus.modules.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DocumentTypeCreateRequest(
        @NotBlank @Pattern(regexp = "^[A-Z]{1,3}$", message = "code must be 1-3 uppercase letters") String code,
        @NotBlank @Size(max = 60) String name,
        @Size(max = 200) String description
) {}

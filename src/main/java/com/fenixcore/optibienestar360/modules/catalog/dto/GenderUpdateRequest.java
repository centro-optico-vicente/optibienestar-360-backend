package com.fenixcore.optibienestar360.modules.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GenderUpdateRequest(
        @NotBlank @Size(max = 20) String name,
        Boolean active
) {}

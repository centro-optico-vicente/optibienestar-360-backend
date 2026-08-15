package com.fenixcore.optibienestar360.modules.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AllyTypeUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 200) String description,
        Boolean active
) {}

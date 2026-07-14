package com.fenixcore.optibienestar360.modules.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StateUpdateRequest(
        @NotBlank @Size(max = 100) String name
) {}

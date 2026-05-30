package com.fenixcore.optisaludplus.modules.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MaritalStatusUpdateRequest(
        @NotBlank @Size(max = 50) String name
) {}

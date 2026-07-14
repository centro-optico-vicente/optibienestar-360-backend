package com.fenixcore.optibienestar360.modules.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DocumentTypeUpdateRequest(
        @NotBlank @Size(max = 60) String name,
        @Size(max = 200) String description
) {}

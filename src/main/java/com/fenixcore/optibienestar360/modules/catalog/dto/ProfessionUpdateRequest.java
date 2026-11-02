package com.fenixcore.optibienestar360.modules.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ProfessionUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 200) String description,
        Boolean active,
        /** Full replace like name/description: {@code null} makes it a top-level profession. */
        UUID parentUuid
) {}

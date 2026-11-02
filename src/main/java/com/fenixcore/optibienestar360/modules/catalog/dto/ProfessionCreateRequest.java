package com.fenixcore.optibienestar360.modules.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ProfessionCreateRequest(
        @NotBlank @Pattern(regexp = "^[A-Z_]{1,40}$", message = "{validation.code.uppercase.long}") String code,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 200) String description,
        /** General profession when this one is a sub-specialty (V213); {@code null} for a top-level one. */
        UUID parentUuid
) {}

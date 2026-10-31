package com.fenixcore.optibienestar360.modules.catalog.dto;

import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.ProfessionalArea;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LicensingBodyCreateRequest(
        @NotBlank @Pattern(regexp = "^[A-Z_]{1,40}$", message = "{validation.code.uppercase.long}") String code,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 200) String description,
        ProfessionalArea professionalArea
) {}

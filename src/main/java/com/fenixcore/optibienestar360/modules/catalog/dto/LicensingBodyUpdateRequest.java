package com.fenixcore.optibienestar360.modules.catalog.dto;

import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.ProfessionalArea;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LicensingBodyUpdateRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 200) String description,
        /** Full replace like name/description: {@code null} means "any area". */
        ProfessionalArea professionalArea,
        Boolean active
) {}

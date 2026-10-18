package com.fenixcore.optibienestar360.modules.catalog.dto;

import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.ProfessionalArea;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.Segment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AllyTypeCreateRequest(
        @NotBlank @Pattern(regexp = "^[A-Z_]{1,40}$", message = "{validation.code.uppercase.long}") String code,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 200) String description,
        /** Defaults to {@code B2B} when omitted. */
        Segment segment,
        /** Only allowed when {@code segment = B2P}. */
        ProfessionalArea professionalArea
) {}

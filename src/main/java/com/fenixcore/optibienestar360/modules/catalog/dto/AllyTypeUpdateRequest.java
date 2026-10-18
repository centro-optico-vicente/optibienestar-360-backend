package com.fenixcore.optibienestar360.modules.catalog.dto;

import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.ProfessionalArea;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.Segment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AllyTypeUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 200) String description,
        Boolean active,
        /**
         * {@code null} keeps the current segment AND area. When present, the
         * area is replaced together with it (send {@code professionalArea}
         * null to clear it, e.g. when moving a type to B2B).
         */
        Segment segment,
        /** Applied on its own only when {@code segment} is null; only allowed for B2P. */
        ProfessionalArea professionalArea
) {}

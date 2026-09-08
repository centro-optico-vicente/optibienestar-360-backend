package com.fenixcore.optibienestar360.modules.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PromoterTypeUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 200) String description,
        /** {@code null} leaves the current value unchanged (PATCH semantics). */
        Boolean generatesHierarchyOverride
) {}

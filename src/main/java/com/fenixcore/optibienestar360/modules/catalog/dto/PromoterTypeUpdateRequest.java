package com.fenixcore.optibienestar360.modules.catalog.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PromoterTypeUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 200) String description,
        /** {@code null} leaves the current value unchanged (PATCH semantics). */
        Boolean generatesHierarchyOverride,
        /** Unlike the flag above, {@code null} clears the cap (full-replace). */
        @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal maxDiscountPct
) {}

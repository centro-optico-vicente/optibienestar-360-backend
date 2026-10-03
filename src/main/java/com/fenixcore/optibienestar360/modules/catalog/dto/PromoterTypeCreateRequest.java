package com.fenixcore.optibienestar360.modules.catalog.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PromoterTypeCreateRequest(
        @NotBlank @Pattern(regexp = "^[A-Z_]{1,40}$", message = "{validation.code.uppercase.long}") String code,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 200) String description,
        /** {@code null} defaults to {@code true} — unchanged behavior (V103). */
        Boolean generatesHierarchyOverride,
        @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal maxDiscountPct
) {}

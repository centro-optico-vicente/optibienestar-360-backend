package com.fenixcore.optibienestar360.modules.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PaymentCategoryCreateRequest(
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,39}$", message = "{validation.code.uppercase.long}") String code,
        @NotBlank @Size(max = 80) String name,
        @Size(max = 255) String description,
        @NotBlank @Pattern(regexp = "^(IN|OUT)$", message = "{validation.payment_category.direction}") String direction
) {}

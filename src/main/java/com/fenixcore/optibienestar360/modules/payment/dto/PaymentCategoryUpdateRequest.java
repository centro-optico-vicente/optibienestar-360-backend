package com.fenixcore.optibienestar360.modules.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code code} is the immutable natural key — not editable here, same as {@code Currency.code}. */
public record PaymentCategoryUpdateRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 255) String description,
        @NotBlank @Pattern(regexp = "^(IN|OUT)$", message = "{validation.payment_category.direction}") String direction,
        Boolean active
) {}

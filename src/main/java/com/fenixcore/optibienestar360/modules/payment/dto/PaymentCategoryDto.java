package com.fenixcore.optibienestar360.modules.payment.dto;

import com.fenixcore.optibienestar360.core.display.Display;

import java.util.UUID;

/** Admin catalog projection for {@code /v1/admin/payment-categories} (V115). */
public record PaymentCategoryDto(
        UUID uuid,
        String code,
        String name,
        String description,
        String direction,
        @Display(Display.Kind.BOOLEAN) boolean active
) {}

package com.fenixcore.optibienestar360.modules.payment.dto;

import com.fenixcore.optibienestar360.core.display.Display;

import java.util.UUID;

/** Admin catalog projection for {@code /v1/admin/payment-methods} (V115). */
public record PaymentMethodDto(
        UUID uuid,
        String code,
        String name,
        @Display(Display.Kind.BOOLEAN) boolean mandatoryBankAccount,
        @Display(Display.Kind.BOOLEAN) boolean mandatoryPhone,
        @Display(Display.Kind.BOOLEAN) boolean mandatoryEmail,
        @Display(Display.Kind.BOOLEAN) boolean mandatoryReferenceNumber,
        @Display(Display.Kind.BOOLEAN) boolean active
) {}

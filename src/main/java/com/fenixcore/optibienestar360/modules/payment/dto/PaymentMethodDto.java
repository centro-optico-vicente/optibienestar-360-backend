package com.fenixcore.optibienestar360.modules.payment.dto;

import com.fenixcore.optibienestar360.core.display.Display;

import java.util.UUID;

/** Admin catalog projection for {@code /v1/admin/payment-methods} (V115). */
public record PaymentMethodDto(
        UUID uuid,
        String code,
        String name,
	String description,
	@Display(Display.Kind.BOOLEAN) boolean mandatoryIdentification,
	@Display(Display.Kind.BOOLEAN) boolean mandatoryBank,
        @Display(Display.Kind.BOOLEAN) boolean mandatoryBankAccount,
	@Display(Display.Kind.BOOLEAN) boolean mandatoryAccountType,
	@Display(Display.Kind.BOOLEAN) boolean mandatoryAccountCode,
        @Display(Display.Kind.BOOLEAN) boolean mandatoryPhone,
        @Display(Display.Kind.BOOLEAN) boolean mandatoryEmail,
        @Display(Display.Kind.BOOLEAN) boolean mandatoryReferenceNumber,
        @Display(Display.Kind.BOOLEAN) boolean requiresApproval,
        @Display(Display.Kind.BOOLEAN) boolean active
) {}

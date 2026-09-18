package com.fenixcore.optibienestar360.modules.bank.dto;

import com.fenixcore.optibienestar360.core.display.Display;

import java.util.UUID;

/** Admin catalog projection for {@code /v1/admin/banks} (V116). */
public record BankDto(
        UUID uuid,
        String code,
        String name,
        String shortName,
        String taxDocumentType,
        String taxDocumentNumber,
        @Display(Display.Kind.BOOLEAN) boolean active
) {}

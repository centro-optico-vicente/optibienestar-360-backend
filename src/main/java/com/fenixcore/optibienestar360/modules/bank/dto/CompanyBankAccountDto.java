package com.fenixcore.optibienestar360.modules.bank.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;

import java.util.UUID;

/** Projection of a company bank account for {@code /v1/admin/company-bank-accounts} and the payer-facing list (V201). */
public record CompanyBankAccountDto(
        UUID uuid,
        @Display DisplayRef bank,
        @Display DisplayRef currency,
        String currency_Code,
        String accountType,
        String accountNumber,
        String holderName,
        String holderDocument,
        String notes,
        int sortOrder,
        @Display(Display.Kind.BOOLEAN) boolean active
) {}

package com.fenixcore.optibienestar360.modules.bank.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.modules.payment.dto.AccountMethodDto;
import com.fenixcore.optibienestar360.core.display.DisplayRef;

import java.util.List;
import java.util.UUID;

/** Projection of a company bank account for {@code /v1/admin/company-bank-accounts} and the payer-facing list (V201). */
public record CompanyBankAccountDto(
        UUID uuid,
        /** Methods the account accepts (V218) — their codes tell the UI which fields to show (e.g. PAGO_MOVIL). */
        List<AccountMethodDto> paymentMethods,
        List<String> paymentMethodCodes,
        @Display DisplayRef bank,
        /** SUDEBAN code of the bank (e.g. 0102) — what a Pago Móvil transfer asks for. */
        String bankCode,
        @Display DisplayRef currency,
        String currency_Code,
        String accountType,
        String accountNumber,
        String holderName,
        String holderDocument,
        String notes,
        String phone,
        String email,
        int sortOrder,
        boolean publicVisible,
        @Display(Display.Kind.BOOLEAN) boolean active
) {}

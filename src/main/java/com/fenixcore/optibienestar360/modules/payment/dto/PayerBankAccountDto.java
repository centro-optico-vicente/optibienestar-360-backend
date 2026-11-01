package com.fenixcore.optibienestar360.modules.payment.dto;

import com.fenixcore.optibienestar360.core.display.Display;

import java.util.List;
import java.util.UUID;

/** An origin account the caller chose to remember (V204); picking it fills the origin fields of a payment line. */
public record PayerBankAccountDto(
        UUID uuid,
        /** Methods the account accepts (V217) — their codes tell the UI which fields apply (e.g. PAGO_MOVIL). */
        List<AccountMethodDto> paymentMethods,
        List<String> paymentMethodCodes,
        @Display DisplayRef bank,
        String accountType,
        String accountCode,
        String accountIdentifier,
        String identification,
        String phone,
        String email,
        String alias
) {}

package com.fenixcore.optibienestar360.modules.payment.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;

import java.util.UUID;

/** An origin account the caller chose to remember (V204); picking it fills the origin fields of a payment line. */
public record PayerBankAccountDto(
        UUID uuid,
        /** Method of the account (V207) — its code tells the UI which fields apply (e.g. PAGO_MOVIL). */
        @Display DisplayRef paymentMethod,
        String paymentMethodCode,
        @Display DisplayRef bank,
        String accountType,
        String accountCode,
        String accountIdentifier,
        String identification,
        String phone,
        String email,
        String alias
) {}

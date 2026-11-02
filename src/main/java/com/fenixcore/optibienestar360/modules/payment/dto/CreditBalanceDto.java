package com.fenixcore.optibienestar360.modules.payment.dto;

import com.fenixcore.optibienestar360.core.display.Display;

import java.math.BigDecimal;
import java.util.List;

/** The caller's credit balance ("saldo a favor") per currency — {@code GET /v1/me/credit-balance} (V201). */
public record CreditBalanceDto(List<Line> balances) {

    public record Line(
            String currency_Code,
            @Display(value = Display.Kind.MONEY, moneyCurrencyField = "currency_Code") BigDecimal amount
    ) {}
}

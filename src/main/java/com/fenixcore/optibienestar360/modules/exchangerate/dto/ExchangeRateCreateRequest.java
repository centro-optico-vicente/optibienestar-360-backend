package com.fenixcore.optibienestar360.modules.exchangerate.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Manual entry — {@code POST /v1/admin/exchange-rates}. {@code source} is
 * always {@code MANUAL} (BCV/EXCHANGE_RATES_API rows only ever come from the
 * not-yet-built {@code FetchExchangeRatesJob}, Tarea 2.13); {@code validFrom}
 * is stamped at {@code now()} — a manual entry is immediately usable, unlike
 * the BCV vigency-date calendar the job will eventually compute.
 */
public record ExchangeRateCreateRequest(
        @NotBlank String baseCurrencyCode,
        @NotBlank String quoteCurrencyCode,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal rate,
        @NotNull LocalDate operationDate
) {}
